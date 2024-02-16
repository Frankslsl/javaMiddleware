package com.hmdp.utils;

import jodd.util.CollectionUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 *
 */

public class SimpleRedisLock implements ILock {

    private StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PREFIX = "lock:";
    private String prefix;
    private static final String ID_PREFIX = UUID.randomUUID().toString() + "-";
    //提前加载脚本,在静态代码块中进行初始化
    private static final DefaultRedisScript<Long> UNLOCK;
    static {
        UNLOCK = new DefaultRedisScript<>();
        //使用ClassPathResource来指定是resources下
        UNLOCK.setLocation(new ClassPathResource("unlock.lua"));
        //设置脚本返回一个long
        UNLOCK.setResultType(Long.class);
    }


    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String prefix) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.prefix = prefix;
    }

    @Override
    public boolean tryLock(long timeoutSet) {
        //获取锁
        //将线程的标识赋值给value,保存在锁中
        //使用uuid来赋值value,这样可以保证唯一,对于极端情况,判断value不至于冲突
        long id = Thread.currentThread().getId();
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + prefix, ID_PREFIX + id + "", timeoutSet, TimeUnit.SECONDS);
        //自动拆箱会有空指针异常的风险,下面这种写法是保证为True的时候返回true,如果是false或者是null则返回false
        return Boolean.TRUE.equals(flag);
    }

    //为了让查找id和最后解锁中间没有空隙,使用lua脚本来让这两个动作合并成原子
    //在resources中写一个lua脚本进行操作

    @Override
    public void unLock() {//调用stringRedisTemplate中的execute方法,调用脚本,需要提前将脚本加载
        stringRedisTemplate.execute(UNLOCK, Collections.singletonList(KEY_PREFIX+prefix), ID_PREFIX + Thread.currentThread().getId());
    }


//    @Override
//    public void unLock() {
//        String threadId = stringRedisTemplate.opsForValue().get(KEY_PREFIX + prefix);
//        String string = ID_PREFIX + Thread.currentThread().getId();
//        if (string.equals(threadId)) {
//            stringRedisTemplate.delete(KEY_PREFIX + prefix);
//        }
//
//    }
}
