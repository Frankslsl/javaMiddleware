package com.hmdp.config;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * 拦截所有,执行大部分的工作,刷新有效期,保存token到线程,但是不判断,全部放行,后面拦截器急性判断
 */
@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {
    //这个类的对象是自己管理的,所以不能使用autowired,只能使用构造函数注入
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //获取session
        log.debug("正在拦截刷新token");
//        HttpSession session = request.getSession();
        //1. 获得请求头中的token(authorization)
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return true;
        }
        //2. 基于token,从redis中取出user
        //使用entris方法取出整个map
        Map map = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY+token);
        if (map.isEmpty()) {
            //4. 对用户进行判断,直接放行
            return true;
        }
        //3. 将查询到的hash数据,转成user
        UserDTO userDTO = BeanUtil.fillBeanWithMap(map, new UserDTO(), false);
        //5. 刷新token的有限期
        stringRedisTemplate.expire(token, LOGIN_USER_TTL, TimeUnit.SECONDS);
        // 存入userHolder并放行
        UserHolder.saveUser(userDTO);
        return true;



        //获得用户用
//        UserDTO user = (UserDTO) session.getAttribute("user");
        //判断是否存在

        //存在,保存再LocalThread,并放行

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
