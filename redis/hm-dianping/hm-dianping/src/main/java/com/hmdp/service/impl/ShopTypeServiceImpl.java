package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_TYPE_LIST;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result getList() {
        String key = SHOP_TYPE_LIST;
        log.debug("正在从内存取出");
        List<String> shopList = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (shopList == null || shopList.isEmpty()) {
            log.debug("取出失败,从数据库取出");
            List<ShopType> sort = query().orderByAsc("sort").list();
            List<String> sortString = sort.stream().map(item -> JSONUtil.toJsonStr(item)).collect(Collectors.toList());
            System.out.println("准备存入缓存");
            stringRedisTemplate.opsForList().rightPushAll(key,sortString);
            return Result.ok(sort);
        }

        log.debug("内存取出成功,返回前端");
        List<ShopType> collect = shopList.stream().map(item -> JSONUtil.toBean(item, ShopType.class)).collect(Collectors.toList());
        return Result.ok(collect);

    }
}
