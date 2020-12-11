package com.miaoshaoProject.mq;

import com.alibaba.fastjson.JSON;
import com.miaoshaoProject.dao.StockLogDOMapper;
import com.miaoshaoProject.dataobject.StockLogDO;
import com.miaoshaoProject.error.BusinessException;
import com.miaoshaoProject.service.OrderService;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;


@Component
public class MqProducer {
    private DefaultMQProducer producer;

    private TransactionMQProducer transactionMQProducer;

    @Value("${mq.nameserver.addr}")
    private String nameAddr;

    @Value("${mq.topicname}")
    private String topicName;

    @Autowired
    private OrderService orderService;

    @Autowired
    private StockLogDOMapper stockLogDOMapper;

    @PostConstruct
    public void init() throws MQClientException {
        producer = new DefaultMQProducer("producer_group");//设置producer的组
        producer.setNamesrvAddr(nameAddr);//设置producer的nameServer
        producer.start();

        transactionMQProducer = new TransactionMQProducer("transaction_producer_group");
        transactionMQProducer.setNamesrvAddr(nameAddr);
        transactionMQProducer.start();

        transactionMQProducer.setTransactionListener(new TransactionListener() {
            @Override
            public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
                //1.本地事务是创建订单
                Integer itemId = (Integer) ((Map)arg).get("itemId");
                Integer promoId = (Integer) ((Map)arg).get("promoId");
                Integer userId = (Integer) ((Map)arg).get("userId");
                Integer amount = (Integer) ((Map)arg).get("amount");
                String stockLogId = (String) ((Map)arg).get("stockLogId");
                try {
                    orderService.createOrder(userId,itemId,promoId,amount,stockLogId);
                } catch (BusinessException e) {
                    //2.1 创建订单失败则不需要扣减本地库存
                    //    创建订单失败将流水状态修改为失败
                    e.printStackTrace();
                    StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
                    stockLogDO.setStatus(3);
                    stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }
                //2.2 创建订单成功则需要扣减本地库存
                return LocalTransactionState.COMMIT_MESSAGE;
            }

            @Override
            public LocalTransactionState checkLocalTransaction(MessageExt msg) {
                //根据是否扣减库存成功，来判断要返回COMMIT,ROLLBACK还是继续UNKNOWN
                String s = new String(msg.getBody());
                Map<String,Object> map = JSON.parseObject(s,Map.class);
                String stockLogId=(String)map.get("stockLogId");
                StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
                //通过订单流水号的状态来判断应该执行什么方法
                if(stockLogDO.getStatus().intValue()==1) return LocalTransactionState.UNKNOW;
                else if(stockLogDO.getStatus().intValue()==2) return LocalTransactionState.COMMIT_MESSAGE;
                else return LocalTransactionState.ROLLBACK_MESSAGE;
            }
        });
    }

    //异步扣减库存
    public boolean transactionAsyncReduceStock(Integer userId,Integer itemId,Integer promoId,Integer amount,String stockLogId){
        //1.bodyMap存库扣减库存的消息
        Map<String,Object> bodyMap = new HashMap<>();
        bodyMap.put("itemId",itemId);
        bodyMap.put("amount",amount);
        bodyMap.put("stockLogId",stockLogId);
        //2.argsMap用来创建订单
        Map<String,Object> argsMap = new HashMap<>();
        argsMap.put("itemId",itemId);
        argsMap.put("amount",amount);
        argsMap.put("userId",userId);
        argsMap.put("promoId",promoId);
        argsMap.put("stockLogId",stockLogId);
        //3.形成扣减库存的消息
        Message message = new Message(topicName,"increase",
                JSON.toJSON(bodyMap).toString().getBytes(Charset.forName("UTF-8")));
        TransactionSendResult sendResult = null;
        try {
            //4.将扣减库存的消息发出，并执行本地事务，message是发送给MQ消费者的半消息，argsMap是用于执行本地事务来形成订单
            sendResult = transactionMQProducer.sendMessageInTransaction(message,argsMap);
        }catch(MQClientException e){
            e.printStackTrace();
            return false;
        }
        //5.这里已经包含了回查的过程和MQ的Consumer消费的过程
        //  如果最终的结果是提交，代表扣减库存在形成订单后完成，返回true。否则代表没有形成订单，返回false
        //  unkown和rollback_message都算失败
        if(sendResult.getLocalTransactionState() == LocalTransactionState.COMMIT_MESSAGE) return true;
        else return false;
    }
}
