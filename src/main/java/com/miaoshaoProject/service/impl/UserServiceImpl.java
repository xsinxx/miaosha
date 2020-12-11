package com.miaoshaoProject.service.impl;

import com.miaoshaoProject.dao.UserDOMapper;
import com.miaoshaoProject.dao.UserPasswordDOMapper;
import com.miaoshaoProject.dataobject.UserDO;
import com.miaoshaoProject.dataobject.UserPasswordDO;
import com.miaoshaoProject.error.BusinessException;
import com.miaoshaoProject.error.EmBusinessError;
import com.miaoshaoProject.service.UserService;
import com.miaoshaoProject.service.model.UserModel;
import com.miaoshaoProject.validator.ValidationResult;
import com.miaoshaoProject.validator.ValidatorImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserDOMapper userDOMapper;
    @Autowired
    private UserPasswordDOMapper userPasswordDOMapper;
    @Autowired
    private ValidatorImpl validator;
    @Autowired
    private RedisTemplate redisTemplate;

    //1.根据id获取用户信息
    @Override
    public UserModel getUserById(Integer id) {
        UserDO userDO = userDOMapper.selectByPrimaryKey(id);
        if(userDO==null) return null;
        UserPasswordDO userPasswordDO = userPasswordDOMapper.selectByUserId(userDO.getId());
        return convertFromDataObject(userDO,userPasswordDO);
    }
    //将UserDao转换成UserModel
    private UserModel convertFromDataObject(UserDO userDO, UserPasswordDO userPasswordDO){
        if(userDO==null) return null;
        UserModel userModel = new UserModel();
        //将整个userDO拷贝到userModel
        BeanUtils.copyProperties(userDO,userModel);
        //设置密码属性，因为userPasswordDO中还含有ID字段，所以不能按照上面的方式拷贝进去，只能直接设置
        if(userPasswordDO!=null)
            userModel.setEncrptyPassword(userPasswordDO.getEncrptPassword());
        return userModel;
    }
    //2.登录方法的实现
    @Override
    @Transactional//保证插入userDo和插入userPasswordDO是在一个事务中
    public void register(UserModel userModel) throws Exception {
        if(userModel==null)
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR);
        //1.调用校验器对名字、性别、年龄、手机号进行校验
        ValidationResult validationResult = validator.validate(userModel);
        if(validationResult.isHasErrors()){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,
                    validationResult.getErrMsg()
            );
        }
        //2.将Model转成userDo进行存储
        UserDO userDO = convertFromModel(userModel);
        try{
            //insertSelective和insert的区别是前者插入前有判空操作
            userDOMapper.insertSelective(userDO);
        }catch(DuplicateKeyException e){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,
                    "手机号重复");
        }
        //这里要再赋值，因为上面的convertFromModel方法会将id取出来了，现在userModel中的id字段为null
        userModel.setId(userDO.getId());
        //3.将密码转成userPasswordDO进行存储
        UserPasswordDO userPasswordDO = convertPasswordFromModel(userModel);
        userPasswordDOMapper.insertSelective(userPasswordDO);
    }

    //将userModel中的密码转换成对应的UserPasswordDO
    private UserPasswordDO convertPasswordFromModel(UserModel userModel){
        if(userModel==null) return null;
        System.out.println(userModel.getId());
        UserPasswordDO userPasswordDO = new UserPasswordDO();
        userPasswordDO.setEncrptPassword(userModel.getEncrptyPassword());
        userPasswordDO.setUserId(userModel.getId());
        return userPasswordDO;
    }
    //将userModel中的属性转换成UserDO
    private UserDO convertFromModel(UserModel userModel){
        if(userModel==null) return null;
        UserDO userDO = new UserDO();
        BeanUtils.copyProperties(userModel,userDO);
        return userDO;
    }
    //3.实现登录功能
    @Override
    public UserModel validateLogin(String telphone, String encrptPassword) throws BusinessException {
        UserDO userDO = userDOMapper.selectByTelphone(telphone);
        //1.先验证用户信息是否存在
        if(userDO==null)
            throw new BusinessException(EmBusinessError.USER_LOGIN_FAIL);

        UserPasswordDO userPasswordDO = userPasswordDOMapper.selectByUserId(userDO.getId());
        UserModel userModel = convertFromDataObject(userDO, userPasswordDO);
        //2.验证用户的密码是否正确
        if(!StringUtils.equals(encrptPassword,userModel.getEncrptyPassword()))
            throw new BusinessException(EmBusinessError.USER_LOGIN_FAIL);

        return userModel;
    }

    @Override
    public UserModel getUserByIdInCache(Integer id) {
        //1.先从redis中取数据
        UserModel userModel=(UserModel)redisTemplate.opsForValue().get("user_validate_"+id);
        //2.redis中没有的话访问mysql，并存储到redis中并设置超时时间
        if(userModel==null){
            userModel=this.getUserById(id);
            redisTemplate.opsForValue().set("user_validate_"+id,userModel);
            redisTemplate.expire("user_validate_"+id,10, TimeUnit.MINUTES);
        }
        //3.返回userModel
        return userModel;
    }
}

