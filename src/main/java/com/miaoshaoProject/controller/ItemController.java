package com.miaoshaoProject.controller;


import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.google.common.util.concurrent.RateLimiter;
import com.miaoshaoProject.controller.viewobject.ItemVO;
import com.miaoshaoProject.error.BusinessException;
import com.miaoshaoProject.error.EmBusinessError;
import com.miaoshaoProject.response.CommonReturnType;
import com.miaoshaoProject.service.ItemService;
import com.miaoshaoProject.service.PromoService;
import com.miaoshaoProject.service.model.CacheService;
import com.miaoshaoProject.service.model.ItemModel;
import org.joda.time.format.DateTimeFormat;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Controller("/item")
@RequestMapping("/item")
@CrossOrigin(origins = {"*"},allowCredentials = "true")
public class ItemController extends BaseController{
    @Autowired
    private ItemService itemService;
    //redis
    @Autowired
    private RedisTemplate redisTemplate;
    //本地缓存
    @Autowired
    private CacheService cacheService;
    //promoService
    @Autowired
    private PromoService promoService;
    //布隆过滤器
    private BloomFilter<Integer> bloomFilter;

    @PostConstruct
    public void init(){
        bloomFilter=BloomFilter.create(Funnels.integerFunnel(),100);//最多有100件商品
    }

    private ItemVO convertVOFromModel(ItemModel itemModel){
        if(itemModel == null){
            return null;
        }
        ItemVO itemVO = new ItemVO();
        BeanUtils.copyProperties(itemModel,itemVO);
        if(itemModel.getPromoModel() != null){
            //有正在进行或即将进行的秒杀活动,
            itemVO.setPromoStatus(itemModel.getPromoModel().getStatus());
            itemVO.setPromoId(itemModel.getPromoModel().getId());
            itemVO.setStartDate(itemModel.getPromoModel().getStartDate().
                    toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")));
            itemVO.setPromoPrice(itemModel.getPromoModel().getPromoItemPrice());
        }else{
            itemVO.setPromoStatus(0);
        }
        return itemVO;
    }

    //商品发布
    @RequestMapping(value = "/publishPromo",method = {RequestMethod.GET})
    @ResponseBody
    public CommonReturnType publishPromo(@RequestParam(name = "id")Integer id){
        bloomFilter.put(id);
        promoService.publishPromo(id);
        return CommonReturnType.create(null);
    }

    //创建商品的controller
    //value是URL,Method是提交的方法,consume是处理请求的提交内容类型
    @RequestMapping(value = "/create",method = {RequestMethod.POST},consumes={CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType createItem(@RequestParam(name = "title")String title,
                                       @RequestParam(name = "description")String description,
                                       @RequestParam(name = "price") BigDecimal price,
                                       @RequestParam(name = "stock")Integer stock,
                                       @RequestParam(name = "imgUrl")String imgUrl) throws BusinessException {
        //封装service请求用来创建商品
        ItemModel itemModel = new ItemModel();
        itemModel.setTitle(title);
        itemModel.setDescription(description);
        itemModel.setPrice(price);
        itemModel.setStock(stock);

        itemModel.setImgUrl(imgUrl);

        ItemModel itemModelForReturn = itemService.createItem(itemModel);
        ItemVO itemVO = convertVOFromModel(itemModelForReturn);

        return CommonReturnType.create(itemVO);
    }

    //商品详情页浏览
    @RequestMapping(value = "/get",method = {RequestMethod.GET})
    @ResponseBody
    public CommonReturnType getItem(@RequestParam(name = "id")Integer id) throws BusinessException {
        //1.请求可能不在数据库中的数据会造成缓存穿透，所以要经过bloom filter
        if(!bloomFilter.mightContain(id))
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        ItemModel itemModel=null;
        //2.先查本地缓存
        itemModel=(ItemModel) cacheService.getFromCommonCache("item_"+id);
        //3.如果本地缓存没有则查Redis，并添加到本地缓存
        if(itemModel==null){
            //4.先查redis
            itemModel=(ItemModel) redisTemplate.opsForValue().get("item_"+id);
            //5.如果redis中没有则查库，并添加到redis中
            if(itemModel==null){
                itemModel = itemService.getItemById(id);
                //添加进redis
                redisTemplate.opsForValue().set("item_"+id,itemModel);
                redisTemplate.expire("item_"+id,10, TimeUnit.MINUTES);
            }
            //添加进本地缓存
            cacheService.setCommonCache("item"+id,itemModel);
        }


        ItemVO itemVO = convertVOFromModel(itemModel);
        return CommonReturnType.create(itemVO);
    }

    //商品列表页面浏览
    @RequestMapping(value = "/list",method = {RequestMethod.GET})
    @ResponseBody
    public CommonReturnType listItem(){
        List<ItemModel> itemModelList = itemService.listItem();
        //使用stream apiJ将list内的itemModel转化为ITEMVO;
        List<ItemVO> itemVOList =  itemModelList.stream().map(itemModel -> {
            ItemVO itemVO = this.convertVOFromModel(itemModel);
            return itemVO;
        }).collect(Collectors.toList());
        return CommonReturnType.create(itemVOList);
    }
}
