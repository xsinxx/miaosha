package com.miaoshaoProject.mq;

import com.alibaba.fastjson.JSON;
import com.miaoshaoProject.dao.ItemStockDOMapper;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.graalvm.compiler.nodes.NodeView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;


@Component
public class MqConsumer {

    private DefaultMQPushConsumer consumer;
    //消费的地址
    @Value("${mq.nameserver.addr}")
    private String nameAddr;
    //消费的topic
    @Value("${mq.topicname}")
    private String topicName;
    //mybatis关于库存的操作
    @Autowired
    private ItemStockDOMapper itemStockDOMapper;
    //@postConstruct注解的作用是完成bean的初始化后执行的操作
    @PostConstruct
    public void init() throws MQClientException {
        //1.指定消费组，指定nameserver的地址(nameserver就是注册中心)，指定消费的主题
        consumer = new DefaultMQPushConsumer("stock_consumer_group");
        consumer.setNamesrvAddr(nameAddr);
        consumer.subscribe(topicName,"*");
        //2.如果broker内有消息了，回调方法执行操作进行消费
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
                //3.实现库存真正到数据库内扣减的逻辑
                Message msg = msgs.get(0);//获取需要扣减的id和数量
                String jsonString  = new String(msg.getBody());
                Map<String,Object>map = JSON.parseObject(jsonString, Map.class);
                Integer itemId = (Integer) map.get("itemId");
                Integer amount = (Integer) map.get("amount");

                itemStockDOMapper.decreaseStock(itemId,amount);//实现真正的扣减库存
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;//返回消费成功的标记
            }
        });
        consumer.start();
    }
}
