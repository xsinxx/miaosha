package com.miaoshaoProject.service.impl;

import com.miaoshaoProject.dao.PromoDOMapper;
import com.miaoshaoProject.dataobject.PromoDO;
import com.miaoshaoProject.error.BusinessException;
import com.miaoshaoProject.error.EmBusinessError;
import com.miaoshaoProject.service.ItemService;
import com.miaoshaoProject.service.PromoService;
import com.miaoshaoProject.service.UserService;
import com.miaoshaoProject.service.model.ItemModel;
import com.miaoshaoProject.service.model.PromoModel;
import com.miaoshaoProject.service.model.UserModel;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class PromoServiceImpl implements PromoService {

    @Autowired
    private PromoDOMapper promoDOMapper;

    @Autowired
    private ItemService itemService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private UserService userService;

    @Override
    public PromoModel getPromoByItemId(Integer itemId) {
        //获取对应商品的秒杀活动信息
        PromoDO promoDO = promoDOMapper.selectByItemId(itemId);

        //dataobject->model
        PromoModel promoModel = convertFromDataObject(promoDO);
        if(promoModel == null){
            return null;
        }

        //1是还未开始，2第正在进行中，3是已经结束
        if(promoModel.getStartDate().isAfterNow()){
            promoModel.setStatus(1);
        }else if(promoModel.getEndDate().isBeforeNow()){
            promoModel.setStatus(3);
        }else{
            promoModel.setStatus(2);
        }
        return promoModel;
    }

    //发布活动商品
    @Override
    public void publishPromo(Integer promoId) {
        //1.通过活动的主键获取活动的信息，活动信息的itemId就是商品的主键
        //  活动不存在直接返回，否则获取商品信息
        PromoDO promoDO = promoDOMapper.selectByPrimaryKey(promoId);
        if(promoDO==null || promoDO.getItemId().intValue()==0) return;
        ItemModel itemModel = itemService.getItemById(promoDO.getItemId());
        //2.将库存同步到商品同步到redis中,key是商品id,value是商品库存
        redisTemplate.opsForValue().set("promo_item_stock_"+itemModel.getId(),itemModel.getStock());
    }

    @Override
    public String generateSecondKillToken(Integer itemId, Integer promoId, Integer userId) throws BusinessException {
        //1.判断库存是否售罄
        if(redisTemplate.hasKey("promo_invalid_"+itemId))
            throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH);
        //2.判断商品信息和用户信息是否存在
        ItemModel itemModel=itemService.getItemByIdInCache(itemId);
        if(itemModel==null) return null;
        UserModel userModel=userService.getUserByIdInCache(userId);
        if(userModel==null) return null;
        //3.判断商品是否在秒杀
        PromoDO promoDO = promoDOMapper.selectByItemId(itemId);
        //3.1 dataobject->model
        PromoModel promoModel = convertFromDataObject(promoDO);
        if(promoModel == null) return null;
        //3.2 1是还未开始，2第正在进行中，3是已经结束
        if(promoModel.getStartDate().isAfterNow()) promoModel.setStatus(1);
        else if(promoModel.getEndDate().isBeforeNow()) promoModel.setStatus(3);
        else promoModel.setStatus(2);

        if(promoModel.getStatus().intValue()!=2) return null;
        //4.生成秒杀的token
        String SecondKillToken = UUID.randomUUID().toString().replace("-", "");
        //5.在redis中缓存商品信息
        redisTemplate.opsForValue().set("promo_token_"+promoId+"_userid_"+userId+"_itemid_"+itemId,SecondKillToken );
        redisTemplate.expire("promo_token_"+promoId+"_userid_"+userId+"_itemid_"+itemId,5, TimeUnit.MINUTES);
        return SecondKillToken;
    }

    private PromoModel convertFromDataObject(PromoDO promoDO){
        if(promoDO == null){
            return null;
        }
        PromoModel promoModel = new PromoModel();
        BeanUtils.copyProperties(promoDO,promoModel);
        promoModel.setPromoItemPrice(new BigDecimal(promoDO.getPromoItemPrice()));
        promoModel.setStartDate(new DateTime(promoDO.getStartDate()));
        promoModel.setEndDate(new DateTime(promoDO.getEndDate()));
        return promoModel;
    }
}
