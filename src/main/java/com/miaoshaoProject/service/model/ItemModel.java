package com.miaoshaoProject.service.model;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

public class ItemModel implements Serializable {
    private Integer id;
    @NotBlank(message = "商品名称不能为空")
    private String title;
    //1.数据库中decimal(5,2)的含义是取值范围是[-999.99,999.99],只能在这个范围内取值。
    //如果是999.67799773,会自动发生截断，截断成999.67
    //2.float用于科学计算,BigDecimal用于商业计算
    @NotNull(message="商品价格不能为空")
    @Min(value=0 ,message="商品价格应该大于0")
    private BigDecimal price;
    @NotNull(message = "库存不能不填写")
    private Integer stock;
    @NotBlank(message="商品描述信息不能为空")
    private String description;
    private Integer sales;
    @NotBlank(message = "图片信息不能为空")
    private String imgUrl;
    //使用聚合模型
    private PromoModel promoModel;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public PromoModel getPromoModel() {
        return promoModel;
    }

    public void setPromoModel(PromoModel promoModel) {
        this.promoModel = promoModel;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getSales() {
        return sales;
    }

    public void setSales(Integer sales) {
        this.sales = sales;
    }

    public String getImgUrl() {
        return imgUrl;
    }

    public void setImgUrl(String imgUrl) {
        this.imgUrl = imgUrl;
    }
}
