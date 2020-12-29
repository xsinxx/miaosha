package com.miaoshaproject.service.impl;

import com.miaoshaproject.dao.OrderDOMapper;
import com.miaoshaproject.dao.SequenceDOMapper;
import com.miaoshaproject.dataobject.OrderDO;
import com.miaoshaproject.dataobject.SequenceDO;
import com.miaoshaproject.error.BussinessException;
import com.miaoshaproject.error.EmBussinessError;
import com.miaoshaproject.service.ItemService;
import com.miaoshaproject.service.OrderService;
import com.miaoshaproject.service.UserService;
import com.miaoshaproject.service.model.ItemModel;
import com.miaoshaproject.service.model.OrderModel;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Service
public class OrderServiceImp implements OrderService {
    @Autowired
    private SequenceDOMapper sequenceDOMapper;

    @Autowired
    private ItemService itemService;

    @Autowired
    private UserService userService;

    @Autowired
    private OrderDOMapper orderDOMapper;

    @Override
    @Transactional
    public OrderModel createOrder(Integer userId, Integer itemId, Integer promoId, Integer amount) throws BussinessException {
        //1.校验下单状态，下单商品是否合法，用户是否合法，购买数量是否正确
        ItemModel itemModel = itemService.getItemById(itemId);
        if(itemModel == null)
            throw new BussinessException(EmBussinessError.PARAMETER_VALIDATION_ERROR,"商品信息不存在");
        if(userService == null)
            throw new BussinessException(EmBussinessError.PARAMETER_VALIDATION_ERROR,"用户不存在");
        if(amount <=0 || amount > 99)
            throw new BussinessException(EmBussinessError.PARAMETER_VALIDATION_ERROR,"数量信息不正确");
        //校验活动信息
        if(promoId != null){
            //(1)校验对应活动是否存在这个商品
            if(promoId.intValue() != itemModel.getPromoModel().getId())
                throw new BussinessException(EmBussinessError.PARAMETER_VALIDATION_ERROR,"活动信息不正确");
            //(2)校验活动是否进行中
            else if (itemModel.getPromoModel().getStatus().intValue() != 2)
                throw new BussinessException(EmBussinessError.PARAMETER_VALIDATION_ERROR,"活动还未开始");
        }
        //2.落单减库存，支付减库存
        boolean result = itemService.decreaseStock(itemId,amount);
        if(!result)
            throw new BussinessException(EmBussinessError.STOCK_NOT_ENOUGH);
        //3.订单入库
        OrderModel orderModel = new OrderModel();
        orderModel.setUserId(userId);
        orderModel.setItemId(itemId);
        orderModel.setAmount(amount);
        if (promoId != null)
            orderModel.setItemPrice(itemModel.getPromoModel().getPromoItemPrice());
        else
            orderModel.setItemPrice(itemModel.getPrice());
        orderModel.setPromoId(promoId);
        orderModel.setOrderAmount(orderModel.getItemPrice().multiply(new BigDecimal(amount)));

        //生成订单号
        orderModel.setId(generateOrderNo());
        OrderDO orderDO = convertFromOrderModel(orderModel);
        orderDOMapper.insertSelective(orderDO);

        //加上商品的销量
        itemService.increaseSales(itemId,amount);
        //4.返回前端
        return orderModel;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    String generateOrderNo(){
        //订单号有16位
        StringBuilder stringBuilder = new StringBuilder();
        //前8位为时间信息
        LocalDateTime now = LocalDateTime.now();
        String nowData = now.format(DateTimeFormatter.ISO_DATE).replace("-","");
        stringBuilder.append(nowData);
        //中间6位为自增序列
        //获取当前sequence
        int sequence = 0;
        SequenceDO sequenceDO = sequenceDOMapper.getSequenceByName("order_info");
        sequence = sequenceDO.getCurrentValue();
        sequenceDO.setCurrentValue(sequenceDO.getCurrentValue()+sequenceDO.getStep());
        sequenceDOMapper.updateByPrimaryKeySelective(sequenceDO);
        String seunenceStr = String.valueOf(sequence);
        for(int i = 0; i < 6-seunenceStr.length(); i++)
            stringBuilder.append(0);
        stringBuilder.append(seunenceStr);

        //最后2位为分库分位表，暂时写死
        stringBuilder.append("00");
        return stringBuilder.toString();
    }

    private OrderDO convertFromOrderModel(OrderModel orderModel){
        if(orderModel==null)
            return null;
        OrderDO orderDO = new OrderDO();
        orderDO.setItemPrice(orderModel.getItemPrice().doubleValue());
        orderDO.setOrderPrice(orderModel.getOrderPrice().doubleValue());
        BeanUtils.copyProperties(orderModel,orderDO);
        return orderDO;
    }
}
