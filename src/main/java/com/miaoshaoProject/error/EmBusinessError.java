package com.miaoshaoProject.error;

public enum EmBusinessError implements CommonError{

    PARAMETER_VALIDATION_ERROR(10001,"参数不合法"),
    UNKNOWN_ERROR(10002,"未知错误"),

    USER_NOT_EXIST(20001,"用户不存在"),
    USER_LOGIN_FAIL(20002,"手机号或密码错误"),
    USER_NOT_LOGIN(20003,"用户还未登录"),
    STOCK_NOT_ENOUGH(30001,"库存不足")
    ;//注意这个分号

    private int ErrCode;
    private String ErrMsg;

    private EmBusinessError(int errCode, String errMsg) {
        ErrCode = errCode;
        ErrMsg = errMsg;
    }

    @Override
    public int getErrCode() {
        return this.ErrCode;
    }

    @Override
    public String getErrMsg() {
        return this.ErrMsg;
    }

    @Override
    public CommonError setErrMsg(String errMsg) {
        this.ErrMsg=errMsg;//将ErrMsg覆盖了，可以实现多种类型
        return this;//返回的是CommError
    }
}
