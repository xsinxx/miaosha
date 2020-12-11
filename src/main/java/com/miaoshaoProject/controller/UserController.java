package com.miaoshaoProject.controller;

import com.alibaba.druid.util.StringUtils;
import com.miaoshaoProject.controller.viewobject.UserVO;
import com.miaoshaoProject.error.BusinessException;
import com.miaoshaoProject.error.EmBusinessError;
import com.miaoshaoProject.response.CommonReturnType;
import com.miaoshaoProject.service.UserService;
import com.miaoshaoProject.service.model.UserModel;
import org.apache.tomcat.util.security.MD5Encoder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.Base64.Encoder;


import javax.servlet.http.HttpServletRequest;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Controller("user")
@RequestMapping("/user")
//防止出现跨域错误
@CrossOrigin(allowCredentials = "true",allowedHeaders = "*")
public class UserController extends BaseController {
    @Autowired
    private UserService userService;

    //httpServletRequest本质是一个代理，其中包含ThreadLocalMap处理线程自身的内容
    @Autowired
    private HttpServletRequest httpServletRequest;
    @Autowired
    private RedisTemplate redisTemplate;

    //用户登录接口
    @RequestMapping(value="/login",method={RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType login(@RequestParam(name="telphone") String telphone,
                                  @RequestParam(name="password") String password
                                  ) throws BusinessException, UnsupportedEncodingException, NoSuchAlgorithmException {
        //1.手机号或密码为空的话参数无效
        if(org.apache.commons.lang3.StringUtils.isEmpty(telphone)
                || org.apache.commons.lang3.StringUtils.isEmpty(password)){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR);
        }
        //2.用户登录服务，验证用户信息
        UserModel userModel = userService.validateLogin(telphone, EncodeByMd5(password));
        //3.没有抛异常代表登录成功
        //redis中将token和用户信息关联起来,并将token返回给客户端,超时时间是1个小时
        String token = UUID.randomUUID().toString();
        //将UUID中产生的-去掉token.replace("-","");
        redisTemplate.opsForValue().set(token,userModel);
        redisTemplate.expire(token,1, TimeUnit.HOURS);

        return CommonReturnType.create(token);
    }

    //用户注册接口
    @RequestMapping(value="/register",method={RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType register(@RequestParam(name="telphone") String telphone,
                                     @RequestParam(name="otpCode") String otpCode,
                                     @RequestParam(name="name") String name,
                                     @RequestParam(name="gender") Integer gender,
                                     @RequestParam(name="age") Integer age,
                                     @RequestParam(name="password") String password
                                     ) throws Exception {
        //1.验证验证码是否正确
        String inSessionOtpCode= (String)httpServletRequest.getSession().getAttribute(telphone);
        System.out.println(inSessionOtpCode);
        if(!StringUtils.equals(inSessionOtpCode,otpCode)){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,
                    "短信验证码不符合");
        }
        //2.用户注册流程
        UserModel userModel = new UserModel();
        userModel.setTelephone(telphone);
        userModel.setName(name);
        userModel.setGender(new Byte(String.valueOf(gender.intValue())));//强转
        userModel.setAge(age);
        userModel.setRegisterMode("byPhone");
        //采用MD5的加密方式
        userModel.setEncrptyPassword(EncodeByMd5(password));

        userService.register(userModel);
        return CommonReturnType.create(null);
    }
    //使用MD5的方式加密
    public String EncodeByMd5(String str) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        Encoder encoder = Base64.getEncoder();
        String s = encoder.encodeToString(md5.digest(str.getBytes("utf-8")));
        return s;
    }

    //用户获取短信验证码
    @RequestMapping(value="/getotp",method={RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType getOpt(@RequestParam(name="telphone") String telphone){
        //1.按照随机数生成OTP验证码
        String otpCode = String.valueOf(new Random().nextInt(99999) + 10000);
        //2.将OTP验证码和用户号手机号关联，使用httpSession的方式绑定手机号和optCode
        //在大厂中常用redis做这部分，首先redis本身是key-value对的形式，其次redis自带过期时间
        httpServletRequest.getSession().setAttribute(telphone,otpCode);
        System.out.println("telphone="+telphone+";optCode="+otpCode);
        String inSessionOtpCode= (String)httpServletRequest.getSession().getAttribute(telphone);
        return CommonReturnType.create(null);
    }

    //将Model转换成View
    private UserVO convertFromModel(UserModel userModel){
        if(userModel==null) return null;
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(userModel,userVO);
        return userVO;
    }
    @RequestMapping("/get")
    @ResponseBody
    /*
    * @Responsebody 后返回结果不会被解析为跳转路径，而是直接写入HTTP 响应正文中。
    * 平常是根据视图解析器将返回的结果和prefix和sufffix进行拼接，再去查找视图
    * */
    public CommonReturnType getUser(@RequestParam(name = "id") Integer id) throws BusinessException {
        UserModel userModel = userService.getUserById(id);
        if(userModel==null)
            throw new BusinessException(EmBusinessError.USER_NOT_EXIST);
        UserVO userVO = convertFromModel(userModel);//转成View
        return CommonReturnType.create(userVO);
    }
}
