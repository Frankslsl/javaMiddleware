--获得锁中的线程标识 get key
local id = redis.call('get', KEYS[1])
--比较线程标识与锁中的标识是否一直

if(id==ARGV[1]) then
    --释放锁
    return redis.call('del', KEYS[1])
end
return 0
