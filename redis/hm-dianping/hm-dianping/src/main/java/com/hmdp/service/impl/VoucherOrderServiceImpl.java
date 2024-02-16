package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdGenerator;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdGenerator redisIdGenerator;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;


    //    @Override
    //初级版本的单线程同步的秒杀,手动数据库判断用户资格
//    public Result seckillVoucher(Long voucherId) {
//        //1. 查询该优惠劵
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        //2. 判断是否开始和已经结束
//        LocalDateTime beginTime = voucher.getBeginTime();
//        LocalDateTime endTime = voucher.getEndTime();
//        LocalDateTime now = LocalDateTime.now();
//        if (beginTime.isAfter(now)) {
//            return Result.fail("秒杀尚未开始");
//        }
//        if (endTime.isBefore(now)) {
//            return Result.fail("秒杀已经结束");
//        }
//
//        //3. 库存是否充足
//        Integer stock = voucher.getStock();
//        if (stock < 1) {
//            return Result.fail("库存不足");
//        }
//        //!所以应该在这个地方加锁,先提交事务,再释放锁
//        //! 这里出现了一个对象的方法,调用了同一个对象的另一个方法,那么会绕开事务,因为事务是代理对象管理的,不是this对象管理
//        Long userId = UserHolder.getUser().getId();
//        //创建锁对象来使用分布式锁来解决多线程不安全的问题
////        SimpleRedisLock simpleRedisLock = new SimpleRedisLock(stringRedisTemplate, "voucherOrder:" + userId);
//        //使用reddisson的锁来替代自己写的锁
//        RLock redissonClock = redissonClient.getLock("couvherOrder:" + userId);
//        if (!redissonClock.tryLock()){//参数分别是重试的等待时间,锁的时间,时间单位.默认值分别是-1,30,TimeUnit.SECOND
////        if (!simpleRedisLock.tryLock(5)){
//            //获取锁失败,证明该用户已经下单
//            return Result.fail("每名用户只能购买一单");
//        }
//        try {
//            //!要使用代理对象,才能激活事务, 该代理对象,就是该实现类对应的对象
//            //! 这里需要引入aspectjweaver这个dependency, 并在主启动类添加注解来暴露这个代理对象@EnableAspectJAutoProxy(exposeProxy = true)
//
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            //使用代理对象来调用方法,并把这个放法也在接口中声明一下
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            //在finally中释放锁,保证有没有异常均释放
////            simpleRedisLock.unLock();
//            redissonClock.unlock();
//        }
//    }
    //加载lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;//这是导入lua脚本

    static {//使用静态代码块导入脚本
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        //使用ClassPathResource来指定是resources下
        SECKILL_SCRIPT.setLocation(new ClassPathResource("secKill.lua"));
        //设置脚本返回一个long
        SECKILL_SCRIPT.setResultType(Long.class);
    }




    //创建线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    //需要在主线程获得代理对象,传到子线程,为了调用同类中的方法,还要保证事务的存在Transactional
    private IVoucherOrderService proxy;


    //服务器初始化的时候,就开启这个子线程,开始监听阻塞队列中,是否有单
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
//    //创建阻塞队列
//    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
//
//    //子线程需要run的程序
//    private class VoucherOrderHandler implements Runnable {
//
//        @Override
//        public void run() {
//            while (true) {
//                try {
//                    //从阻塞队列中取出order
//                    VoucherOrder voucherOrder = orderTasks.take();
//                    handleVoucherOrder(voucherOrder);
//                } catch (Exception e) {
//                    log.error("处理订单异常", e);
//                    ;
//                }
//            }
//        }
//    }

    //基于消息队列集,取出消息队列中的order
    private class VoucherOrderHandler implements Runnable{
        String queueName = "stream.orders";
        @Override
        public void run() {
            while (true){
                try{
                    //1. 从消息队列获取订单信息 XREADGROUP GROUP g1 consumerName COUNT 1 BLOCK 2000 STREAMS stream.orders >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //2. 判断消息获取是否成功
                    if (list ==null || list.isEmpty()){
                        //2.1 如果获取失败,则说明没有订单在消息队列,continue,继续监听
                        continue;
                    }
                    //2.2 解析取出的订单,String为消息的id, 后面是map形式的键值对
                    MapRecord<String, Object, Object> entries = list.get(0);
                    Map<Object, Object> value = entries.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    //2.2 如果成功,则可以下单
                    handleVoucherOrder(voucherOrder);
                    //2.3 下单成功之后,向消息队列ACK确认订单处理成功 SACK stream.orders g1 entries.getId()
                    Long g1 = stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", entries.getId());
                }catch(Exception e){
                    log.error("正在从消息队列取出order,获取order异常",e);
                    handlePendingList();
                }
            }
        }
        private void handlePendingList(){//处理pendingList中的order
            while (true){
                try{
                    //1. 从消息队列获取订单信息 XREADGROUP GROUP g1 consumerName COUNT 1 STREAMS stream.orders 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    //2. 判断消息获取是否成功
                    if (list ==null || list.isEmpty()){
                        //2.1 如果获取失败,则说明没有订单在消息队列,结束本循环,跳出到外循环
                        break;
                    }
                    //2.2 解析取出的订单,String为消息的id, 后面是map形式的键值对
                    MapRecord<String, Object, Object> entries = list.get(0);
                    Map<Object, Object> value = entries.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    //2.2 如果成功,则可以下单
                    handleVoucherOrder(voucherOrder);
                    //2.3 下单成功之后,向消息队列ACK确认订单处理成功 SACK stream.orders g1 entries.getId()
                    Long g1 = stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", entries.getId());
                }catch(Exception e){
                    log.error("处理pendingList,获取order异常",e);
                }
            }
        }
    }

    //处理阻塞队列中取出的order
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //这里是多线程的一个新线程,所以不能从Thread中获取Userid了
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        //这里可以不加锁,因为前段redis已经做了判断
        if (!isLock) {
            log.error("不能重复下单");
            return;
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    //实际在数据库中sql数据库中根据redis中的order表单来创建真实的表单,因为单号已经在redis执行阶段返回给前端,这里不需要返回数据
    //同样因为redis的lua中已经判断了用户资格和库存情况,这里可以不使用锁,包括乐观锁进行判断了
    //以下是使用消息阻塞队列来优化实现的业务
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //获取用户
//        UserDTO user = UserHolder.getUser();
//        Long userId = user.getId();
//        //1. 执行lua脚本
//        Long execute = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());
//        //2. 判断结果是否为0
//        int result = execute.intValue();
//        //2.1 不为零代表没有购买资格
//        if (result != 0) {
//            return Result.fail(result == 1 ? "库存不足" : "不能重复下单");
//        }
//        //2.2 为0,有购买资格,把下单信息保存在阻塞队列中
//        //创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        long orderId = redisIdGenerator.nextId("order");
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(userId);
//        voucherOrder.setVoucherId(voucherId);
//        //创建阻塞队列
//        orderTasks.add(voucherOrder);
//        //获取代理对象,将代理对象作为成员变量放在上面,这样子线程就可以通过成员变量获得这个代理对象
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//
//        return Result.ok(orderId);
//    }

    //!以下是使用redis消息队列集的形式来实现业务
    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        long orderId = redisIdGenerator.nextId("order");//创建订单id,传入到lua脚本中进行消息的发送
        //1. 执行lua脚本
        Long execute = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString(), String.valueOf(orderId));
        //2. 判断结果是否为0
        int result = execute.intValue();
        //2.1 不为零代表没有购买资格
        if (result != 0) {
            return Result.fail(result == 1 ? "库存不足" : "不能重复下单");
        }
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);
    }



    @Transactional //异步队列的方法
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();

        // ? 6.1 查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        // ? 判断该用户是否已经购买了订单
        if (count > 0) {
            log.error("正在异步操作数据库,出现用户重复购买情况");
            return;
        }

        // 4. 扣减库存,这里只要判断stock>0就可以进行抵扣了-->这个属于利用乐观锁来处理超卖问题.只要减少库存的同时保证stock>0,就可以
        boolean done = seckillVoucherService.update().setSql("stock = stock -1").eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0).update();
        if (!done) {
            log.error("正在异步操作数据库,扣减库存过程出现错误");
        }
        //将订单写入数据库
        boolean save = save(voucherOrder);
        if (!save) {
            log.error("正在在数据库中创建最终订单,出现错误");
            return;
        }
    }

//    @Transactional // 传统的同步的方法
//    public Result createVoucherOrder(Long voucherId) {//不要将synchronized加在方法上,会所有人串行,会影响性能
//        // ? 6. 一人一单的业务, 会有线程问题,因为是添加,所以不能使用CAS乐观锁的方法解决,只能加悲观锁
//        Long userId = UserHolder.getUser().getId();//因为同一个用户不能重复,所以使用userId作为锁,更加合理
//        /**
//         * 因为要保证是值一样,而不是对象一样,所以将userId转成toString,
//         * 因为toString底层也是new一个String,那么还会造成值同样id过来new成了不同的对象.
//         *
//         * 这里使用String 的intern()方法,该方法会到常量池搜索,是否有同样的常量,可以保证是值的锁
//         *
//         * synchronized在方法中加锁,但是整个方法被事务管理.
//         * 会出现,先释放了锁,但是数据库的更新并没有事务递交,这时会有线程进来,还是会出现线程安全问题
//         * 所以我们应该将整个函数锁定,先事务提交,再释放锁. 所以锁应该加再调用函数的地方,将整个函数锁定
//         * 转55行
//         */
//
//        // ? 6.1 查询订单
//        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//        // ? 判断该用户是否已经购买了订单
//        if (count > 0) {
//            Result.fail("不能重复购买");
//        }
//
//        // 4. 扣减库存,这里只要判断stock>0就可以进行抵扣了-->这个属于利用乐观锁来处理超卖问题.只要减少库存的同时保证stock>0,就可以
//        boolean done = seckillVoucherService.update().setSql("stock = stock -1").eq("voucher_id", voucherId).gt("stock", 0).update();
//        if (!done) {
//            return Result.fail("错误,秒杀失败,请重试");
//        }
//        //5. 创建订单
//        VoucherOrder order = new VoucherOrder();
//        //生成订单id
//        //使用自己封装的RedisIdGererator来生成全局唯一id
//        long id = redisIdGenerator.nextId("order");
//        order.setId(id);
//        //填入用户id
//        userId = UserHolder.getUser().getId();
//        order.setUserId(userId);
//        //填入代金卷id
//        order.setVoucherId(voucherId);
//        //将订单写入数据库
//        boolean save = save(order);
//        if (!save) {
//            return Result.fail("代金卷秒杀错误,请重试");
//        }
//        //6. 返回订单id
//        return Result.ok(id);
//
//    }
}


