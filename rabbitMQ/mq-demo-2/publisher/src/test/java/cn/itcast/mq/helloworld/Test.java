package cn.itcast.mq.helloworld;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.runner.RunWith;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.UUID;

/**
 *
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class Test {
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @org.junit.Test
    public void TestPublish() throws InterruptedException {
        String queueName = "simple.queue";
        String message = "hello hello!!!";
        //消息的唯一标识
        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
        //添加confirm的callback,也就是消息到达交换机的时候,给出的反馈


        correlationData.getFuture().addCallback(
                result -> {
                    if (result.isAck()) {//消息投送到了交换机
                        log.info("发送到交换机成功, ID:{}", correlationData.getId());
                    } else if (!result.isAck()) {//nack,消息没有投送到交换机
                        log.error("发送到交换机失败, ID{}, 原因{}", correlationData.getId(), result.getReason());
                    }
                },
                ex -> {
                    log.error("消息发送异常, ID{}, 原因{}", correlationData.getId(), ex.getMessage());
                }
        );
        HashMap<String, Object> map = new HashMap<>();
        map.put("name", "sunlei");
        map.put("age", 11);
        String jsonStr = JSONUtil.toJsonStr(map);
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);;
        messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        Message msg = MessageBuilder.withBody(jsonStr.getBytes()).andProperties(messageProperties).setExpiration("5000").build();
        rabbitTemplate.send("ttl.direct", "ttl", msg, correlationData);


        Thread.sleep(1000);
    }
    @org.junit.Test
    public void test2(){
        Message msg = MessageBuilder.withBody("haha".getBytes()).setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT).build();
        for (int i = 0; i < 10000000; i++) {
        rabbitTemplate.convertAndSend("lazy_exchange","lazy",msg);
        }
    }
    @org.junit.Test
    public void test3(){
        Message msg = MessageBuilder.withBody("haha".getBytes()).setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT).build();
        for (int i = 0; i < 10000000; i++) {
            rabbitTemplate.convertAndSend("normal_exchange","normal",msg);
        }
    }

}
