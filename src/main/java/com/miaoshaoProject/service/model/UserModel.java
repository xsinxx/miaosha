package com.miaoshaoProject.service.model;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

//在属性上加的注释用于校验
public class UserModel implements Serializable {
    private Integer id;
    //@NotBlank只能作用在String上，不能为null，而且调用trim()后，长度必须大于0
    //如果发生上面的情况就会报message中的内容
    @NotBlank(message="用户名不能为空")
    private String name;
    //@NotNull注解的作用是不能为null，如果性别填写null，会报message中的内容
    @NotNull(message="性别不能不填")
    //@Min规定最小值，如果比value小的话会报message.@Max同理于@Min
    @Min(value=0,message="年龄必须大于0")
    @Max(value=150,message="年龄必须小于150")
    private Byte gender;
    private Integer age;
    @NotBlank(message="手机号不能为空")
    private String telephone;
    private String registerMode;
    private String thirdPartyId;
    @NotBlank(message="密码不能为空")
    private String encrptyPassword;

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getEncrptyPassword() {
        return encrptyPassword;
    }

    public void setEncrptyPassword(String encrptyPassword) {
        this.encrptyPassword = encrptyPassword;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Byte getGender() {
        return gender;
    }

    public void setGender(Byte gender) {
        this.gender = gender;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public String getRegisterMode() {
        return registerMode;
    }

    public void setRegisterMode(String registerMode) {
        this.registerMode = registerMode;
    }

    public String getThirdPartyId() {
        return thirdPartyId;
    }

    public void setThirdPartyId(String thirdPartyId) {
        this.thirdPartyId = thirdPartyId;
    }
}
