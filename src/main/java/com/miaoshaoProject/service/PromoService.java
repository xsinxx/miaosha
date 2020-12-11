package com.miaoshaoProject.service;

import com.miaoshaoProject.error.BusinessException;
import com.miaoshaoProject.service.model.PromoModel;
import org.springframework.stereotype.Component;


public interface PromoService {
    //根据itemid获取即将进行的或正在进行的秒杀活动
    PromoModel getPromoByItemId(Integer itemId);
    //活动发布
    void publishPromo(Integer promoId);
    //生成秒杀令牌
    String generateSecondKillToken(Integer itemId,Integer promoId,Integer userId) throws BusinessException;
}
