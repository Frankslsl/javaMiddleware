package cn.itcast.mq.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 */
@Configuration
public class ErrorMessageConfig {
    @Bean("error_exchange")
    public Exchange errorExchange(){
        return ExchangeBuilder.directExchange("error_exchange").durable(true).build();
    }
    @Bean("error_queue")
    public Queue errorQueue(){
        return QueueBuilder.durable("error.queue").build();
    }
@Bean
    public Binding errorBinding(){
        return BindingBuilder.bind(errorQueue()).to(errorExchange()).with("error").noargs();
    }
    @Bean
    public MessageRecoverer republishMessageRecoverer(RabbitTemplate rabbitTemplate){
        return new RepublishMessageRecoverer(rabbitTemplate,"error_exchange", "error");
    }
}
