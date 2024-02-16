package com.hmdp.utils;

import ch.qos.logback.classic.spi.EventArgUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 *
 */
@Component
public class RedisIdGenerator {
    private static final long BEGIN_TIMESTAMP = 1640995200L;//自定义一个时间参照物,使用LocalDateTime.of(参照物).toEpochSecond(ZoneOffset.UTC)来获得
    private static final long COUNT_BITS = 32;//留出给序列号的位数
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix){
        //生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long epochSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = epochSecond - BEGIN_TIMESTAMP;
        //生成序列号,需要更换key
        String dateStamp = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //使用基础类型,便于后续的计算
        long increment = stringRedisTemplate.opsForValue().increment("icrId:" + keyPrefix + ":" + dateStamp + ":");//redis的自增长有上限,是2^64,我们采用32位来统计,所以拼接日期时间戳来方便统计并避免溢出
        //拼接并返回
        //使用位运算, 把前面32位空出来,再填入
        return timeStamp<<COUNT_BITS | increment;
    }
}
