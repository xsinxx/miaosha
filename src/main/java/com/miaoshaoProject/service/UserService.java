package com.miaoshaoProject.service;

import com.miaoshaoProject.error.BusinessException;
import com.miaoshaoProject.service.model.UserModel;

public interface UserService {
    public UserModel getUserById(Integer id);
    //注册
    void register(UserModel userModel) throws Exception;
    //登录
    UserModel validateLogin(String telphone,String encrptPassword) throws BusinessException;
    //通过缓存获取用户对象
    UserModel getUserByIdInCache(Integer id);
}
