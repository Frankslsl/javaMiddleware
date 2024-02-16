package cn.itcast.mq;

import cn.hutool.core.bean.BeanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 *
 */
@Component
@Slf4j
public class SpringRabbitListener {
    @RabbitListener(queues = "simple.queue")
    public void listenSimpleQueueMessage(Map<String, Object> msg){
        log.warn("消费者接受到的消息是{}", msg.toString());
        //添加confirm的callback,也就是消息到达交换机的时候,给出的反馈
        log.info("消费者消息处理成功");
    }

    @RabbitListener(queues = "dl.queue")
    public void listenDlQueue(Map<String, Object> msg){
        log.warn("使用死信交换机实现延迟消息,收到消息:{}",msg.toString());
    }
}
