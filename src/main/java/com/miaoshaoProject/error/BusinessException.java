package com.miaoshaoProject.error;

//业务异常
public class BusinessException extends Exception implements CommonError{
    private CommonError commonError;

    public BusinessException(CommonError commonError){
        super();//要继承Exception
        this.commonError=commonError;
    }

    public BusinessException(CommonError commonError,String errMsg){
        super();
        this.commonError=commonError;
        this.commonError.setErrMsg(errMsg);
    }

    @Override
    public int getErrCode() {
        return commonError.getErrCode();
    }

    @Override
    public String getErrMsg() {
        return commonError.getErrMsg();
    }

    @Override
    public CommonError setErrMsg(String errMsg) {
        commonError.setErrMsg(errMsg);
        return this;
    }
}
