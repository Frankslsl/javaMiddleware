package cn.itcast.mq.helloworld;

import cn.itcast.mq.RabbitMQConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;

/**
 *
 */
@SpringBootTest
@RunWith(SpringRunner.class)
public class SpringAMPQ {
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Test
    public void Publish(){
        HashMap<String, Object> msg = new HashMap<>();
        msg.put("name","frank");
        msg.put("age", 21);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, "boot.#", msg);
    }
}
