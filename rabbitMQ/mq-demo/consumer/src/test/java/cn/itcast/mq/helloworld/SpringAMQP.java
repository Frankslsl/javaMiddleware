package cn.itcast.mq.helloworld;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Map;
import java.util.Objects;

/**
 *
 */

@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
public class SpringAMQP {

    @RabbitListener(queues = "boot_topic_queue")
    public void listener(Map<String, Object> msg){
        System.out.println(msg);
    }
}
