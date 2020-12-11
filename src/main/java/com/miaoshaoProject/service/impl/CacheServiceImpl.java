package com.miaoshaoProject.service.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.miaoshaoProject.service.model.CacheService;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;


@Service
public class CacheServiceImpl implements CacheService {

    private Cache<String,Object> commonCache = null;

    //@PostConstruct的作用在SpringMVC的初始化后进行加载
    @PostConstruct
    public void init(){
        commonCache = CacheBuilder.newBuilder()
                //设置缓存容器的初始容量为10
                .initialCapacity(10)
                //设置缓存中最大可以存储100个KEY,超过100个之后会按照LRU的策略移除缓存项，这个对应的是内存淘汰策略
                .maximumSize(100)
                //设置写缓存后多少秒过期，这个对应的是Redis中的数据删除策略
                .expireAfterWrite(60, TimeUnit.SECONDS).build();
    }

    @Override
    public void setCommonCache(String key, Object value) {
            commonCache.put(key,value);
    }

    @Override
    public Object getFromCommonCache(String key) {
        return commonCache.getIfPresent(key);
    }
}
