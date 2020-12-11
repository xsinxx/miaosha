package com.miaoshaoProject.validator;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.Set;

@Component
public class ValidatorImpl implements InitializingBean {
    private Validator validator;
    public ValidationResult validate(Object bean)
    {
        //返回值
        ValidationResult validationResult = new ValidationResult();
        //1.如果某个属性违背了校验规则就会将该属性放到set中
        Set<ConstraintViolation<Object>> violationSet = validator.validate(bean);
        //2.如果set中有属性证明有属性不满足校验规则
        if(violationSet.size()>0){
            validationResult.setHasErrors(true);
            violationSet.forEach((constraintViolation)->{
                //获取错误信息
                String message = constraintViolation.getMessage();
                //获取属性
                String propertyName = constraintViolation.getPropertyPath().toString();
                //添加到对应的map中
                validationResult.getErrorMsgMap().put(message,propertyName);
            });
        }
        return validationResult;
    }
    @Override
    public void afterPropertiesSet() throws Exception {
        validator= Validation.buildDefaultValidatorFactory().getValidator();
    }
}
