package cn.itcast.mq.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 */
@Configuration
public class LazyQ {
    @Bean("lazy_exchange")
    public Exchange lazyExchange(){
        return ExchangeBuilder.directExchange("lazy_exchange").build();
    }
    @Bean("lazy_queue")
    public Queue lazyQueue(){
        return QueueBuilder.durable("lazy_queue").lazy().build();
    }
    @Bean
    public Binding lazyBinding(){
        return BindingBuilder.bind(lazyQueue()).to(lazyExchange()).with("lazy").noargs();
    }

    @Bean("normal_Exchange")
    public Exchange normalExchange(){
        return ExchangeBuilder.directExchange("normal_exchange").build();
    }
    @Bean("normal_queue")
    public Queue normalQueue(){
        return QueueBuilder.durable("normal_queue").build();
    }
    @Bean
    public Binding normalBinding(){
        return BindingBuilder.bind(normalQueue()).to(normalExchange()).with("normal").noargs();
    }
}
