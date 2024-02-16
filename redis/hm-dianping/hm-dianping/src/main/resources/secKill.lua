--1. 参数列表
--1.1 优惠劵id
local voucherId = ARGV[1]
--1.2 用户id
local userId = ARGV[2]
--1.3 order 的id
local orderId = ARGV[3]

--2 数据key
--2.1库存key
local stockKey = 'seckill:stock:'..voucherId
--2.2订单key
local orderKey = 'seckill:order:'..voucherId
--3.脚本业务
--3.1判断库存是否充足 get stockKey, 将得到的字符串,转成number
if (tonumber(redis.call('get',stockKey))<=0) then
    --库存不足
    return 1
end

if (redis.call('SISMEMBER',orderKey,userId)==1) then
    --3.3 存在,则证明是重复下单,返回2
    return 2
end

--证明用户有资格
--3.4扣除库存incrby stockKey -1
redis.call('incrby',stockKey,-1)
--3.5下单
redis.call('sadd', orderKey,userId)

--4 消息队列的代码开始
--4.1 首先在命令行手动创建消息队列集 : XGROUP CREATE stream.order g1 o MKSTREAM
--4.2 将订单发送消息给消息队列中 XADD stream.orders * key1 value1
redis.call('xadd','stream.orders','*','userId', userId,'voucherId', voucherId,'id',orderId)
return 0