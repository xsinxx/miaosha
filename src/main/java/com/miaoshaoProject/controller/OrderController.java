package com.miaoshaoProject.controller;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.google.common.util.concurrent.RateLimiter;
import com.miaoshaoProject.error.BusinessException;
import com.miaoshaoProject.error.EmBusinessError;
import com.miaoshaoProject.mq.MqProducer;
import com.miaoshaoProject.response.CommonReturnType;
import com.miaoshaoProject.service.ItemService;
import com.miaoshaoProject.service.OrderService;
import com.miaoshaoProject.service.PromoService;
import com.miaoshaoProject.service.model.OrderModel;
import com.miaoshaoProject.service.model.UserModel;
import com.miaoshaoProject.util.CodeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;

@Controller("order")
@RequestMapping("/order")
@CrossOrigin(origins = {"*"},allowCredentials = "true")
public class OrderController extends BaseController {
    @Autowired
    private OrderService orderService;

    @Autowired
    private HttpServletRequest httpServletRequest;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private ItemService itemService;

    @Autowired
    private PromoService promoService;

    @Autowired
    private com.miaoshaoProject.mq.MqProducer MqProducer;

    private ExecutorService threadPool;
    //令牌桶
    private RateLimiter rateLimiter;


    @PostConstruct
    public void init(){
        threadPool= Executors.newFixedThreadPool(20);
        rateLimiter=RateLimiter.create(300);//令牌桶最多有300个线程
    }
    //生成验证码
    @RequestMapping(value="/generateverifycode",method={RequestMethod.GET,RequestMethod.POST})
    @ResponseBody
    public void generateVerifyCode(HttpServletResponse response) throws IOException,BusinessException {
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if(token == null)
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户还未登陆，不能下单");
        UserModel userModel=(UserModel) redisTemplate.opsForValue().get(token);
        if(userModel==null)
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户还未登录，不能获取验证码");
        Map<String, Object> map = CodeUtil.generateCodeAndPic();//调用验证码的生成类来生成验证码
        System.out.println("验证码："+map.get("code"));
        redisTemplate.opsForValue().set("verify_code_"+userModel.getId(),map.get("code"));
        redisTemplate.expire("verify_code_"+userModel.getId(),5,TimeUnit.MINUTES);

        ImageIO.write((RenderedImage) map.get("codePic"), "jpeg", response.getOutputStream());
    }
    //生成秒杀令牌
    @RequestMapping(value = "/generatetoken",method = {RequestMethod.POST},consumes={CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType generatetoken(@RequestParam(name="itemId")Integer itemId,
                                          @RequestParam(name="promoId")Integer promoId,
                                          @RequestParam(name="verifyCode") String verifyCode) throws BusinessException {
        //1.根据token获取用户信息
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if(StringUtils.isEmpty(token))
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户还未登陆，不能下单");
        //2.获取用户的登陆信息
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if(userModel == null)
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户还未登陆，不能下单");
        //3.校验验证码
        String code= (String)redisTemplate.opsForValue().get("verify_code_" + userModel.getId());
        if(code==null || !code.equals(verifyCode))
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"验证码错误");
        //4.获取秒杀访问令牌
        String promoToken = promoService.generateSecondKillToken(itemId,promoId,userModel.getId());
        if(promoToken == null)
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"生成令牌失败");
        //5.返回对应的结果
        return CommonReturnType.create(promoToken);
    }

    //封装下单请求
    @RequestMapping(value = "/createorder",method = {RequestMethod.POST},consumes={CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType createOrder(@RequestParam(name="itemId")Integer itemId,
                                        @RequestParam(name="amount")Integer amount,
                                        @RequestParam(name="promoId",required = false)Integer promoId,
                                        @RequestParam(name="promoToken",required = false)String promoToken) throws BusinessException {
        //1.使用guava的令牌桶算法来限流
        if(!rateLimiter.tryAcquire())
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"活动火爆，请稍后再试");
        //2.先判断是否登录了,Boolean是包装类型，所以才有booleanValue()方法
        String token=httpServletRequest.getParameterMap().get("token")[0];
        if(token==null)
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户还未登陆，不能下单");
        //3.获取用户的登陆信息
        UserModel userModel=(UserModel)redisTemplate.opsForValue().get(token);
        if(userModel==null)
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户还未登陆，不能下单");
        //4.校验秒杀令牌是否正确
        if(promoId==null){
            String SecondKillToken=(String)redisTemplate.opsForValue().get("promo_token_"+promoId+"_userid_"+userModel.getId()+"_itemid_"+itemId);
            if(SecondKillToken==null || !SecondKillToken.equals(promoToken))
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"秒杀令牌校验失败");
        }
        //5.队列泄洪
        //同步调用线程池的submit方法,会返回一个future对象。线程池中的线程就是通过这个future对象和主线程交互
        //拥塞窗口为20的等待队列，用来队列化泄洪
        Future<Object> future = threadPool.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                //4.初始化流水
                String initstockLog = itemService.initstockLog(itemId, amount);
                //5.异步扣减库存，先形成订单再扣减库存
                if(!MqProducer.transactionAsyncReduceStock(userModel.getId(),itemId,promoId,amount,initstockLog))
                    throw new BusinessException(EmBusinessError.UNKNOWN_ERROR,"MQ发送消息失败");
                return null;
            }
        });

        try {
            future.get();//这个方法会一直阻塞主线程，直到线程池中的线程返回future
        } catch (InterruptedException e) {
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        } catch (ExecutionException e) {
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        }

        return CommonReturnType.create(null);
    }
}