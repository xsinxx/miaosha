package com.miaoshaoProject.error;

import com.miaoshaoProject.response.CommonReturnType;

public interface CommonError {
    public int getErrCode();
    public String getErrMsg();
    public CommonError setErrMsg(String errMsg);
}
