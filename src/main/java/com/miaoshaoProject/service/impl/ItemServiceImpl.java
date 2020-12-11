package com.miaoshaoProject.service.impl;

import com.miaoshaoProject.dao.ItemDOMapper;
import com.miaoshaoProject.dao.ItemStockDOMapper;
import com.miaoshaoProject.dao.StockLogDOMapper;
import com.miaoshaoProject.dataobject.ItemDO;
import com.miaoshaoProject.dataobject.ItemStockDO;
import com.miaoshaoProject.dataobject.StockLogDO;
import com.miaoshaoProject.error.BusinessException;
import com.miaoshaoProject.error.EmBusinessError;
import com.miaoshaoProject.mq.MqProducer;
import com.miaoshaoProject.service.ItemService;
import com.miaoshaoProject.service.PromoService;
import com.miaoshaoProject.service.model.ItemModel;
import com.miaoshaoProject.service.model.PromoModel;
import com.miaoshaoProject.validator.ValidationResult;
import com.miaoshaoProject.validator.ValidatorImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ItemServiceImpl implements ItemService {
    @Autowired
    private ValidatorImpl validator;
    @Autowired
    ItemDOMapper itemDOMapper;
    @Autowired
    private ItemStockDOMapper itemStockDOMapper;
    @Autowired
    private PromoService promoService;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private MqProducer mqProducer;//消息队列的生产者
    @Autowired
    private StockLogDOMapper stockLogDOMapper;

    //将ItemModel转成ItemDo返回出去
    private ItemDO convertItemDoFromItemModel(ItemModel itemModel){
        if(itemModel==null) return null;
        ItemDO itemDO = new ItemDO();
        BeanUtils.copyProperties(itemModel,itemDO);
        itemDO.setPrice(itemModel.getPrice().doubleValue());
        return itemDO;
    }
    //将ItemModel转成ItemStock返回出去
    private ItemStockDO convertItemStockFromItemModel(ItemModel itemModel){
        if(itemModel==null) return null;
        ItemStockDO itemStockDO = new ItemStockDO();
        itemStockDO.setItemId(itemModel.getId());
        itemStockDO.setStock(itemModel.getStock());
        return itemStockDO;
    }
    @Override
    @Transactional
    public ItemModel createItem(ItemModel itemModel) throws BusinessException {
        //1.利用校验器来校验入参
        ValidationResult result = validator.validate(itemModel);
        if(result.isHasErrors())
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,result.getErrMsg());
        //2.转化ItemModel-->DataObject,并写入数据库
        ItemDO itemDO = convertItemDoFromItemModel(itemModel);

        itemDOMapper.insertSelective(itemDO);
        itemModel.setId(itemDO.getId());

        ItemStockDO itemStockDO = convertItemStockFromItemModel(itemModel);

        itemStockDOMapper.insertSelective(itemStockDO);
        //3.将对应的ItemModel返回？？？？？？
        return getItemById(itemModel.getId());
    }

    @Override
    public List<ItemModel> listItem() {
        List<ItemDO> itemDOList = itemDOMapper.listItem();
        List<ItemModel> itemModelList =  itemDOList.stream().map(itemDO -> {
            ItemStockDO itemStockDO = itemStockDOMapper.selectByItemId(itemDO.getId());
            ItemModel itemModel = this.convertModelFromDataObject(itemDO,itemStockDO);
            return itemModel;
        }).collect(Collectors.toList());
        return itemModelList;
    }

    //将DataObject转成Model
    private ItemModel convertModelFromDataObject(ItemDO itemDO,ItemStockDO itemStockDO){
        ItemModel itemModel = new ItemModel();
        BeanUtils.copyProperties(itemDO,itemModel);
        itemModel.setPrice(new BigDecimal(itemDO.getPrice()));
        itemModel.setStock(itemStockDO.getStock());

        return itemModel;
    }
    @Override
    public ItemModel getItemById(Integer id) {
        //1.获取商品信息
        ItemDO itemDO = itemDOMapper.selectByPrimaryKey(id);
        if(itemDO==null) return null;
        //2.获取库存信息，itemDO的id就是itemStock的ItemId
        ItemStockDO itemStockDO = itemStockDOMapper.selectByItemId(itemDO.getId());
        //3.将商品信息和库存信息合并成item信息
        ItemModel itemModel = convertModelFromDataObject(itemDO, itemStockDO);
        //4.获取商品的活动信息
        PromoModel promoModel = promoService.getPromoByItemId(itemModel.getId());
        //不等于3代表活动还未结束
        if(promoModel != null && promoModel.getStatus().intValue() != 3){
            itemModel.setPromoModel(promoModel);
        }
        return itemModel;
    }

    @Override
    public ItemModel getItemByIdInCache(Integer id) {
        //1.从缓存中拿数据,传输进去的是key
        ItemModel itemModel = (ItemModel)redisTemplate.opsForValue().get("item_validate_" + id);
        //2.如果数据不存在就去数据库中拿并放入缓存
        if(itemModel==null){
            itemModel=this.getItemById(id);
            redisTemplate.opsForValue().set("item_validate_"+id,itemModel);
            redisTemplate.expire("item_validate_"+id,10, TimeUnit.MINUTES);
        }
        //3.返回这个模型
        return itemModel;
    }

    @Override
    @Transactional
    public boolean decreaseStock(Integer itemId, Integer amount) throws BusinessException {
        //1.减去正数就是加上负数,返回的remain是缓存中还剩下多少值
        long remain=redisTemplate.opsForValue()
                .increment("promo_item_stock_"+itemId,amount.intValue()*(-1));
        //2.remain大于等于0代表缓存的数量足够删除，那么现在更新数据库，利用消息队列来防止更新数据库失败
        if(remain>0) return true;
        else if(remain==0){
            redisTemplate.opsForValue().set("promo_invalid_"+itemId,true);
            return true;
        }
        else{
            //需要的库存太多了，扣减失败需要将缓存加上
            redisTemplate.opsForValue().increment("promo_item_stock_"+itemId,amount.intValue());
            return false;
        }
    }

    @Override
    @Transactional
    public void increaseSales(Integer itemId, Integer amount) throws BusinessException {
        itemDOMapper.increaseSales(itemId,amount);
    }

    @Override
    @Transactional
    public String initstockLog(Integer itemId, Integer amount) {
        StockLogDO stockLogDO = new StockLogDO();
        stockLogDO.setItemId(itemId);
        stockLogDO.setAmount(amount);
        stockLogDO.setStatus(1);
        stockLogDO.setStockLogId(UUID.randomUUID().toString().replace("-",""));

        stockLogDOMapper.insertSelective(stockLogDO);

        return stockLogDO.getStockLogId();
    }
}
