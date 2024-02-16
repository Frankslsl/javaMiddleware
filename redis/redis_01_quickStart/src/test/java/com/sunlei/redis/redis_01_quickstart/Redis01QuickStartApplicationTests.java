package com.sunlei.redis.redis_01_quickstart;

import ch.qos.logback.core.util.CloseUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunlei.redis.redis_01_quickstart.pojo.User;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.ReactiveStreamCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

@SpringBootTest
@Slf4j
class Redis01QuickStartApplicationTests {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testString() throws JsonProcessingException {
        //写入数据
        redisTemplate.opsForValue().set("name", "frank");
        //获取数据
        Object name = redisTemplate.opsForValue().get("name");
        redisTemplate.opsForValue().set("user:1",new User("frank", 12));
        User o = (User) redisTemplate.opsForValue().get("user:1");
        //自动序列化器会存入一个序列化规则,所以推荐统一使用String,手动序列化json
        User sunlei = new User("sunlei", 19);
        //手动序列化器
        ObjectMapper mapper = new ObjectMapper();
        String sunleiJson = mapper.writeValueAsString(sunlei);

        stringRedisTemplate.opsForValue().set("user:2",sunleiJson );
        String s = stringRedisTemplate.opsForValue().get("user:2");
        User user2 = mapper.readValue(s, User.class);
        log.info("user2 is {}", user2.toString());

        log.info(o.getName());
        log.info(o.toString());
        log.info(name.toString());
    }
    @Test
    void testHash(){
        stringRedisTemplate.opsForHash().put("hash:1","name","frank");
        stringRedisTemplate.opsForHash().put("hash:1", "age", "12");

        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries("hash:1");
        List<Object> values = stringRedisTemplate.opsForHash().values("hash:1");
        log.info(entries.toString());


    }

}
