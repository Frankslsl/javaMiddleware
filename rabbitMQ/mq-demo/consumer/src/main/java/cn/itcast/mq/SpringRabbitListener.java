package cn.itcast.mq;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 *
 */
@Component
public class SpringRabbitListener {

    @RabbitListener(queues = "boot_topic_queue")
    public void listenSimpleQueueMessage(Map<String, Object> msg){
        System.out.println(msg);
    }


}
