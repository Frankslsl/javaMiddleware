package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate redis;

    //获得锁的方法
    private boolean tryLock(String key) {
        Boolean flag = redis.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        //不能直接返回,需要使用基础类型返回,因为拆箱的过程中有可能是空指针
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    private void unlock(String key) {
        redis.delete(key);
    }

    @Override
    //用互斥锁解决缓存击穿
    public Result queryById(Long id) {
        //解决缓存穿透的问题
        Shop shop = queryWithPenetration(id);
        //解决缓存击穿的问题,使用互斥锁来解决
//          Shop shop = querywithMutex(id);
        //解决缓存击穿的问题,使用逻辑过期来解决
//        Shop shop = queryWithLogicalExpire(id);
        return shop == null ? Result.fail("店铺不存在") : Result.ok(shop);
    }

    //更新数据库,然后删除缓存
    @Override
    //通过事务来控制原子性
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        Boolean delete = redis.delete(CACHE_SHOP_KEY + id);

        return Result.ok();
    }

    //backup一下缓存穿透的代码
    public Shop queryWithPenetration(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //1. 从redis中查询商铺缓存
        String shopJson = redis.opsForValue().get(key);
        //2. 判断是否存在
        //3. 存在则直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shopBean = JSONUtil.toBean(shopJson, Shop.class);
            return shopBean;
        }
        //查看的时候,要看是否查到的这个shopJson是不是空值,也就是为了防止击穿而保存的空对象,如果是空值,则拦截,不是空值则放行到数据库


        if (shopJson == "") {
            log.debug("阻止穿透");
            //直接拦截返回错误信息
            return null;
        }
        //4. 不存在,根据id从数据库中查询
        Shop shop = getById(id);
        //5. 不存在,返回错误
        if (BeanUtil.isEmpty(shop)) {
            //增加解决缓存穿透的业务流程
            // 返回一个空对象保存再redis中,并设置expire
            redis.opsForValue().set(key, "", CACHE_SHOP_TTL, TimeUnit.MINUTES);
            //返回
            return null;
        }
        //6. 存在,把数据写入redis,并设置有效时间
        redis.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //7. 返回
        return shop;

    }

    //通过互斥锁来解决缓存击穿的问题
    public Shop querywithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //1. 从redis中查询商铺缓存
        Shop shop = null;

        String shopJson = redis.opsForValue().get(key);
        //2. 判断是否存在
        //3. 存在则直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shopBean = JSONUtil.toBean(shopJson, Shop.class);
            return shopBean;
        }
        //查看的时候,要看是否查到的这个shopJson是不是空值,也就是为了防止击穿而保存的空对象,如果是空值,则拦截,不是空值则放行到数据库
        //!不能单独使用==""来判断,需要使用shopJson!=null来进行判断

        if (shopJson == "") {
            log.debug("阻止穿透");
            //直接拦截返回错误信息
            return null;
        }
        //未命中,实现缓存重建
        //4.1 获取锁
        try {
            boolean flag = tryLock(LOCK_SHOP_KEY);
            //4.2 判断是否成功
            //4.3 如果获取失败,则休眠然后重试
            if (!flag) {
                Thread.sleep(50);
                //重试则是使用递归
                return querywithMutex(id);
            }


            //4.4 获取成功,则从数据库查询
            //4. 不存在,根据id从数据库中查询
            shop = getById(id);
            //设置休眠体现效果
            Thread.sleep(500);
            //5. 不存在,返回错误
            if (BeanUtil.isEmpty(shop)) {
                //增加解决缓存穿透的业务流程
                // 返回一个空对象保存再redis中,并设置expire
                redis.opsForValue().set(key, "", CACHE_SHOP_TTL, TimeUnit.MINUTES);
                //返回
                return null;
            }
            //6. 存在,把数据写入redis,并设置有效时间
            redis.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error(e.getMessage());
            log.error(e.getCause().toString());
            throw new RuntimeException(e);
        } finally {
            //4.5 释放互斥锁
            unlock(LOCK_SHOP_KEY);
        }

        //7. 返回
        return shop;
    }

    //使用线程池来进行重建
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    //使用逻辑过期来解决缓存击穿的问题
    public Shop queryWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //1. 从redis中查询商铺缓存
        String shopJson = redis.opsForValue().get(key);
        //2. 判断是否存在
        //3. 因为提前做了缓存预热且是逻辑过期,没有真正设置TTL,所以理论上肯定存在,如果不存在就是请求的问题,所以没有命中则直接返回null
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        //4. 命中,首先把json反序列化
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        //5. 判断是否过期, 因为反序列化依赖的是类型,指定的data是Object类型,那么反序列话的结果是个JSONObject,再次使用JSONUtil的方法,来转化成实际你想要的实例
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            //5.1 未过期,直接返回店铺信息
            return shop;
        }
        //5.2 过期,需要缓存重建
        //6.1 尝试获取锁
        String lockKey = LOCK_SHOP_KEY + shop.getId();
        Boolean flag = redis.opsForValue().setIfAbsent(lockKey, "1", LOCK_SHOP_TTL, TimeUnit.MINUTES);
        if (flag) {
            //6.2 是否获取锁成功
            //6.3 成功则开启线程实现重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    saveShop2Redis(shop.getId(), 20L);
                    Thread.sleep(50);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        return shop;
    }

    //用来预热缓存的方法
    public void saveShop2Redis(Long id, Long seconds) {
        //1. 查询店铺数据
        Shop shop = getById(id);
        log.debug(shop.toString());
        //2 封装成RedisData包括逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(seconds));
        //3. 写入redis
        redis.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

}
