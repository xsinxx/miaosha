package com.miaoshaproject.service.impl;

import com.miaoshaproject.dao.UserDOMapper;
import com.miaoshaproject.dao.UserPasswordDOMapper;
import com.miaoshaproject.dataobject.UserDO;
import com.miaoshaproject.dataobject.UserPasswordDO;
import com.miaoshaproject.error.BussinessException;
import com.miaoshaproject.error.EmBussinessError;
import com.miaoshaproject.service.UserService;
import com.miaoshaproject.service.model.UserModel;
import com.miaoshaproject.validator.ValidationResult;
import com.miaoshaproject.validator.ValidatorImp;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserDOMapper userDOMapper;

    @Autowired
    private UserPasswordDOMapper userPasswordDOMapper;

    @Autowired
    private ValidatorImp validator;

    @Override
    public UserModel getUserById(Integer id) {
        //调用userdomapper获取到对应的用户dataobject
        UserDO userDO = userDOMapper.selectByPrimaryKey(id);
        if(userDO == null) {
            return null;
        }
        //通过用户id获取对应的用户加密密码信息
        UserPasswordDO userPasswordDO = userPasswordDOMapper.selectByUserId(userDO.getId());

        return  converFromDataObject(userDO,userPasswordDO);
    }

    @Override
    @Transactional//保证user和password在一个事务里面
    public void register(UserModel userModel) throws BussinessException {
        if(userModel==null)
            throw new BussinessException(EmBussinessError.PARAMETER_VALIDATION_ERROR);
        //1.调用校验器对名字、性别、年龄、手机号进行校验
        ValidationResult validationResult = validator.validate(userModel);
        if(validationResult.isHasErrors()){
            throw new BussinessException(EmBussinessError.PARAMETER_VALIDATION_ERROR,
                    validationResult.getErrMsg()
            );
        }
        //2.将Model转成userDo进行存储
        UserDO userDO = convertFromModel(userModel);
        try{
            //insertSelective和insert的区别是前者插入前有判空操作
            userDOMapper.insertSelective(userDO);
        }catch(DuplicateKeyException e){
            throw new BussinessException(EmBussinessError.PARAMETER_VALIDATION_ERROR,
                    "手机号重复");
        }
        //这里要再赋值，因为上面的convertFromModel方法会将id取出来了，现在userModel中的id字段为null
        userModel.setId(userDO.getId());
        //3.将密码转成userPasswordDO进行存储
        UserPasswordDO userPasswordDO = convertPasswordFromModel(userModel);
        userPasswordDOMapper.insertSelective(userPasswordDO);
    }

    //登录功能
    @Override
    public UserModel validateLogin(String telephone, String encrptPassword) throws BussinessException {
        //通过用户的手机获取用户信息
        UserDO userDO = userDOMapper.selectByTelephone(telephone);
        if (userDO == null)
            throw new BussinessException((EmBussinessError.PARAMETER_VALIDATION_ERROR));
        UserPasswordDO userPasswordDO = userPasswordDOMapper.selectByUserId(userDO.getId());
        UserModel userModel = converFromDataObject(userDO,userPasswordDO);

        //比对用户信息内加密的密码是否和传输进来的密码相匹配
        if(!StringUtils.equals(encrptPassword,userModel.getEncrptPassword()))
            throw new BussinessException(EmBussinessError.USER_LOGIN_FAIL);

        return userModel;

    }

    private UserPasswordDO convertPasswordFromModel(UserModel userModel){
        if(userModel==null)
            return null;
        UserPasswordDO userPasswordDO = new UserPasswordDO();
        userPasswordDO.setEncrptPassword(userModel.getEncrptPassword());
        userPasswordDO.setUserId(userModel.getId());
        return userPasswordDO;
    }
    private UserDO convertFromModel(UserModel userModel){
        if(userModel == null)
            return null;
        UserDO userDO = new UserDO();
        BeanUtils.copyProperties(userModel,userDO);

        return userDO;
    }

    private UserModel converFromDataObject(UserDO userDO, UserPasswordDO userPasswordDO){
        if(userDO == null) {
            return null;
        }
        UserModel userModel = new UserModel();
        BeanUtils.copyProperties(userDO,userModel);
        if(userPasswordDO != null)
            userModel.setEncrptyPassword(userPasswordDO.getEncrptPassword());
        return userModel;
    }
}
