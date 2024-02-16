package cn.itcast.mq.config;

import com.rabbitmq.client.AMQP;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 */
@Configuration
public class TTLconfig {
    @Bean
    public Exchange ttlExchange(){
        return ExchangeBuilder.directExchange("ttl.direct").durable(true).build();
    }
    @Bean
    public Queue ttlQueue(){
        return QueueBuilder.durable("ttl.queue").ttl(10000).deadLetterExchange("dl.direct").deadLetterRoutingKey("dl").build();
    }
    @Bean
    public Binding binding(){
        return BindingBuilder.bind(ttlQueue()).to(ttlExchange()).with("ttl").noargs();
    }

    @Bean
    public Exchange dlExchange(){
        return ExchangeBuilder.directExchange("dl.direct").durable(true).build();
    }
    @Bean
    public Queue dlQueue(){
        return QueueBuilder.durable("dl.queue").build();
    }
    @Bean
    public Binding dlBinding(){
        return BindingBuilder.bind(dlQueue()).to(dlExchange()).with("dl").noargs();
    }
}
