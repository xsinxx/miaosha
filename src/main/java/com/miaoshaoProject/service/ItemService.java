package com.miaoshaoProject.service;

import com.miaoshaoProject.error.BusinessException;
import com.miaoshaoProject.service.model.ItemModel;

import java.util.List;

public interface ItemService {
    //创建商品
    ItemModel createItem(ItemModel itemModel) throws BusinessException;
    //商品列表浏览，将所有的商品查询出来
    List<ItemModel> listItem();
    //商品详情浏览
    ItemModel getItemById(Integer id);
    //Item 以及promo model缓存模型
    ItemModel getItemByIdInCache(Integer id);
    boolean decreaseStock(Integer itemId,Integer amount) throws BusinessException;
    //商品销量增加
    void increaseSales(Integer itemId,Integer amount)throws BusinessException;

    String initstockLog(Integer itemId,Integer amount);

}
