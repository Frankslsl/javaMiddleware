package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.TimeoutUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 *
 */
@Slf4j
@Component
public class CachClient {

    private final StringRedisTemplate stringRedisTemplate;

    //内部类,来封装逻辑过期时间类型的处理
    class RedisDataForLogic {
        private LocalDateTime expireTime;
        private Object data;

        public RedisDataForLogic(LocalDateTime expireTime, Object data) {
            this.expireTime = expireTime;
            this.data = data;
        }

        public LocalDateTime getExpireTime() {
            return expireTime;
        }

        public void setExpireTime(LocalDateTime expireTime) {
            this.expireTime = expireTime;
        }

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }
    }

    public CachClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //普通存

    /**
     * @param key      redis中的key
     * @param value    reids中的value
     * @param time     过期事件
     * @param timeUnit 过期时间单位
     */
    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);

    }

    //逻辑过期

    /**
     * @param key      redis中的key
     * @param value    redis中的value
     * @param time     过期时间
     * @param timeUnit 过期时间的单位
     */
    public void setLogic(String key, Object value, Long time, TimeUnit timeUnit) {
        //使用redisDataLogic来封装,保证有value和expireTime
        RedisDataForLogic redisDataForLogic = new RedisDataForLogic(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)), value);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisDataForLogic));
    }

    //防止缓存穿透的方法

    /**
     * @param keyPrefix  redis中key的前缀
     * @param id         需要查的id,可以是任意类型
     * @param type       数据库中查出的,需要返回的实体类
     * @param dbFallBack 在数据库中查询的具体方法 == 在使用的时候,通过 lambda表达式的方法传  id->getById(id)或者this::getById
     * @param time       过期时间
     * @param timeUnit   过期时间单位
     * @param <R>        返回类型,也就是数据库返回的封装的实体类,对应上面的type
     * @param <ID>       数据库中要查询的键的值,对应上面的id
     * @return
     */
    public <R, ID> R queryWithPenetration(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Long time, TimeUnit timeUnit) {//Function 是有参函数
        //拼接key
        String key = keyPrefix + id;
        //redis查找
        String objectJsonStr = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(objectJsonStr)) {
            //如果找到直接返回结果
            return JSONUtil.toBean(objectJsonStr, type);
        }
        //没有找到,去数据库找, 判断objectJsonStr是不是"", 来防止穿透
        if (Objects.equals(objectJsonStr, "")) {
            return null;
        }
        //使用用户穿进来的查询数据库的方法,来查询数据库,获得结果
        R r = dbFallBack.apply(id);
        //如果结果为null,在redis中存储空value来防止穿透
        if (BeanUtil.isEmpty(r)) {
            set(key, "", time, timeUnit);
            return null;
        }
        set(key, r, time, timeUnit);
        return r;
    }

    //缓存击穿
    //定义所需要的线程池
    private static final ExecutorService CATCH_EXCUTOR_POOL = Executors.newFixedThreadPool(10);

    /**
     *
     * @param keyPrefix  redis中key的前缀
     * @param id id,可以为任意类型
     * @param type 数据库查出来的结果所要封装的实体类
     * @param lockPrefix 锁的前缀
     * @param dbFallBack 在数据库中查询的具体方法 == 在使用的时候,通过 lambda表达式的方法传  id->getById(id)或者this::getById
     * @param time 过期时间
     * @param timeUnit 过期时间单位
     * @return
     * @param <R> 返回类型,对应上面的type
     * @param <ID> 查询的参数,对应上面的id
     */
    public <R, ID> R requireWithLogicalExpire(String keyPrefix, ID id, Class<R> type, String lockPrefix, Function<ID, R> dbFallBack, Long time, TimeUnit timeUnit) {
        //拼接key
        String key = keyPrefix + id;
        //redis中查找相应数据
        String objectJsonStr = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(objectJsonStr)) {
            //因为有缓存预热,所以如果返回为空,则证明为传来的请求有问题,直接返回null
            return null;
        }
        //不为空,则要判断是否逻辑过期
        RedisData redisData = JSONUtil.toBean(objectJsonStr, RedisData.class);
        //! 得到的data是一个JSONObject对象,所以强转一下
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            //如果没有过期,则直接返回r
            return r;
        }
        //如果过期,重建缓存
        //获得锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockPrefix + id, "1");
        if (flag) {
            //获得锁成功,开启线程重建缓存
            CATCH_EXCUTOR_POOL.submit(() -> {
                try {
                    R newR = dbFallBack.apply(id);
                    setLogic(key, newR, time, timeUnit);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    stringRedisTemplate.delete(lockPrefix + id);
                }
            });
        }
        return r;
    }
}
