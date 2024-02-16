package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.keyvalue.repository.KeyValueRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IUserService userService;

    @Override
    public Result isFollow(Long id) {
        //查询是否关注,根据userId
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", id).eq("follow_user_id", userId).select().count();
        return Result.ok(count > 0);
    }

    @Override
    public Result follow(Long id, Boolean isFollow) {//id是被关注的用户id
        //判断到底是关注还是取关
        //user和userId是请求关注的userId
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        if (isFollow) {
            //关注,新增数据
            log.debug("正在关注");
            Follow follow = new Follow();
            follow.setFollowUserId(userId);
            follow.setUserId(id);
            save(follow);
            stringRedisTemplate.opsForSet().add("following:" + userId, id.toString());
        } else {
            //取关,删除数据
            log.debug("正在去管");
            remove(new QueryWrapper<Follow>().eq("user_id", id).eq("follow_user_id", userId));
            stringRedisTemplate.opsForSet().remove("following:" + userId, id.toString());
        }

        return Result.ok();
    }

    @Override
    public Result followCommon(Long targetUserId) {
        //获得当前登陆用户
        Long userId = UserHolder.getUser().getId();
        //数据库查出两个用户的关注列表
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect("following:" + targetUserId, "following:" + userId);
        if (intersect.isEmpty() || intersect == null){
            return Result.ok(Collections.emptyList());
        }
        List<Long> common = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<User> users = userService.listByIds(common);
        List<UserDTO> userDTOs = users.stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOs);


    }
}
