package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import jodd.util.CollectionUtil;
import jodd.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IFollowService followService;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            isLiked(blog.getId());
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        queryBlogUser(blog);
        //这里要查看这个用户是否给这个blog点过赞
        isLiked(id);
        return Result.ok(blog);
    }

    @Override
    public void likeBlog(Long id) {
        //1. 判断当前的用户,是否已经点赞
        //获取当前用户,因为不登录也可以查看该页面,所以要判断一下UserHolder中有没有用户
        UserDTO user = UserHolder.getUser();
        if(user ==null){
            return;
        }
        Long userId = user.getId();
        //2. 获得这个blog
        Blog blog = query().eq("id", id).one();

        String key = BLOG_LIKED_KEY + Long.toString(id);

        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {//score等于null
            //数据库点赞数+!
            boolean isSuccess = update().setSql("liked = liked+1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            boolean isSuccess = update().setSql("liked = liked-1").eq("id", id).update();
            if (isSuccess) {
                Long remove = stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id.toString();
        //从redis的该blog的点赞的zset中提取时间戳前5, range key 0 4
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5==null || top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //接续其中用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        //根据id查询用户
        List<Blog> users = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        List<UserDTO> userDTOS = users.stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        //返回
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文

        boolean save = save(blog);
        if (!save) {
            return Result.fail("保存笔记出现问题");
        }
        // 查询笔记作者的所有follower
        Long userId = UserHolder.getUser().getId();
        List<Follow> followUsers = followService.query().eq("follow_user_id", userId).list();
        if (followUsers ==null || followUsers.isEmpty()){
            return Result.ok(blog.getId());
        }
        //推送笔记id给所有follower
        List<Long> followUserIds = followUsers.stream().map(Follow::getUserId).collect(Collectors.toList());
        followUserIds.forEach(id -> stringRedisTemplate.opsForZSet().add(FEED_KEY + id, blog.getId().toString(),System.currentTimeMillis()));
        // 返回id
        return Result.ok(blog.getId());

    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //找到用户,并找到用户的收件箱
        UserDTO user = UserHolder.getUser();
        if (BeanUtil.isEmpty(user)){
            return Result.ok();
        }
        Long userId = user.getId();
        String key = FEED_KEY + userId;
        //判断是第几次查询

        Set<ZSetOperations.TypedTuple<String>> typedTuples1 = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        //非空判断
        if (typedTuples1 == null || typedTuples1.isEmpty()){
            return Result.ok();
        }
        //获取id
        List<String> values = typedTuples1.stream().map(ZSetOperations.TypedTuple::getValue).collect(Collectors.toList());
        //获取时间戳
        //解析得到的数据,blogId,minTime(最小时间戳),offset
        long miniTime = 0;
        int os = 1;
        List<Long> scores = typedTuples1.stream().map(element->element.getScore().longValue()).collect(Collectors.toList());
        for (Long score : scores) {
            if (score == miniTime){
                os++;
            }else {
                miniTime = score;
                os = 1;
            }
        }

        //根据id查询blog,必须也要有序,所以要拼接sql进行查询
        String join = StrUtil.join(",", values);
        List<Blog> blogs = query().in("id", values).last("ORDER BY FIELD(id," + join + ")").list();
        //每一个blog填入blogUser和isBlogLiked
        blogs.forEach(blog -> {queryBlogUser(blog);isLiked(blog.getId());});
        log.debug(blogs.toString());
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(miniTime);
        return Result.ok(scrollResult);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }

    private void isLiked(Long id) {
        Blog blog = getById(id);
        //获得user
        UserDTO user = UserHolder.getUser();
        if (BeanUtil.isEmpty(user)){
            return;
        }
        Long userId = UserHolder.getUser().getId();
        //查看redis中是否点过赞,为了达到能导出最近5个点赞的人功能,使用SortedSet来进行存储
        String key = BLOG_LIKED_KEY + Long.toString(id);
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            blog.setIsLike(false);
        } else {
            blog.setIsLike(true);
        }
    }
}
