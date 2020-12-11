package com.miaoshaoProject.controller;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.miaoshaoProject.error.BusinessException;
import com.miaoshaoProject.error.EmBusinessError;
import com.miaoshaoProject.response.CommonReturnType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

//其他所有的Controller都继承BaseController,这是所有都需要的方法
//里面的@ExceptionHandler是异常拦截类，所以所有的Controller都需要
public class BaseController {
    /*数据被编码成以 '&' 分隔的键-值对, 同时以 '=' 分隔键和值*/
    public final static String CONTENT_TYPE_FORMED="application/x-www-form-urlencoded";
    /*
     * 1.当一个Controller中有方法加了@ExceptionHandler之后，
     * 这个Controller其他方法中没有捕获的异常就会以参数的形式传入加了@ExceptionHandler注解的那个方法中。
     * 2.@ResponseStatus作用就是改变服务器响应的状态码,
     * 比如一个本是200的请求可以通过@ResponseStatus 改成404/500等等.
     * 3.@Responsebody 后返回结果不会被解析为跳转路径，而是直接写入HTTP响应正文中。
     * 平常是根据视图解析器将返回的结果和prefix和sufffix进行拼接，再去查找视图
     * */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public Object handlerException(HttpServletRequest request, Exception ex){
        Map<String,Object> responseData=new HashMap<>();
        //如果不是BusinessException要做特殊处理，这里报未知错误
        if(ex instanceof BusinessException){
            BusinessException businessException=(BusinessException)ex;
            responseData.put("errCode",businessException.getErrCode());
            responseData.put("errMsg",businessException.getErrMsg());
        }
        else
        {
            responseData.put("errCode", EmBusinessError.UNKNOWN_ERROR.getErrCode());
            responseData.put("errMsg",EmBusinessError.UNKNOWN_ERROR.getErrMsg());
        }
        return CommonReturnType.create(responseData,"fails");
    }
}
