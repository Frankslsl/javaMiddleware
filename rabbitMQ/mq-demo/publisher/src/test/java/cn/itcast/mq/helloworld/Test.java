package cn.itcast.mq.helloworld;

import lombok.extern.slf4j.Slf4j;
import org.junit.runner.RunWith;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

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
    public void TestPublish(){
        String queueName = "simple.queue";
        String message = "hello, spring amqp!!!";

        try {
            rabbitTemplate.convertAndSend(queueName,message);
        } catch (AmqpException e) {
           log.error(e.getMessage());
        }
        log.info("11");
    }
}
