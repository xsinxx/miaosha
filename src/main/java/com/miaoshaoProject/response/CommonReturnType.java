package com.miaoshaoProject.response;

public class CommonReturnType {
    private String status;
    private Object data;

    //若status="success",则返回jason数据;若status="fail",则data内使用通用的错误码格式
    //两个函数形成了重载
    public static CommonReturnType create(Object result){
        CommonReturnType commonReturnType = new CommonReturnType();
        commonReturnType.setData(result);
        commonReturnType.setStatus("success");
        return commonReturnType;
    }

    public static CommonReturnType create(Object Result,String staus){
        CommonReturnType commonReturnType = new CommonReturnType();
        commonReturnType.setStatus(staus);
        commonReturnType.setData(Result);
        return commonReturnType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
