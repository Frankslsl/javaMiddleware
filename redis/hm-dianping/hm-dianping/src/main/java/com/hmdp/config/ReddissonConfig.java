package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 */
@Configuration
public class ReddissonConfig {
    @Bean
    public RedissonClient redissonClient() {
        //设置配置文件
        Config config = new Config();
        config.useSingleServer().setAddress("redis://20.14.93.178:6379").setPassword("wodediannao").setDatabase(1);
        //创建redisClient工具对象
        return Redisson.create(config);
    }
}
