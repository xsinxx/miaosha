package com.miaoshaproject.controller;

import com.miaoshaproject.error.BussinessException;
import com.miaoshaproject.error.EmBussinessError;
import com.miaoshaproject.response.CommonReturnType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

public class BaseController {

    public static final String CONTENT_TYPE_FORMED = "application/x-www-form-urlencoded";
    //定义exceptionhandler解决未被controller层吸收的exception
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public Object handlerExceotion(HttpServletRequest request, Exception ex){
        Map<String,Object> responseDate = new HashMap<>();
        if(ex instanceof BussinessException){
            BussinessException bussinessException = (BussinessException)ex;
            responseDate.put("errCode",bussinessException.getErrCode());
            responseDate.put("errMsg",bussinessException.getErrMsg());
        }else{
            responseDate.put("errCode", EmBussinessError.UNKNOWN_ERROR.getErrCode());
            responseDate.put("errMsg",EmBussinessError.UNKNOWN_ERROR.getErrMsg());
        }
        return CommonReturnType.create(responseDate,"fail");
    }
}
