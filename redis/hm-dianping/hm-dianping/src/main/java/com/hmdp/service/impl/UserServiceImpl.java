package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import jodd.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

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
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result sendCode(String phone) {
        //1. 校验手机号,使用正则表达式检查
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2. 不符合,返回错误信息
            return Result.fail("手机号格式错误");
        }
        //3. 符合,生成验证码
        String code = RandomUtil.randomNumbers(6);
        //4. 保存验证码
//        session.setAttribute("code", code);
        //4. redis: 保存道redis中,手机号为key,给key设置一个prefix的常量,和一个expire的常量
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5. 发送验证码
        log.debug("发送验证码成功,验证码: {}", code);
        //6. 返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        String codeEnter = loginForm.getCode();
        String phone = loginForm.getPhone();
        //1. 校验手机号和验证码
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            //2. 不符合,返回错误信息
            return Result.fail("手机号格式错误");
        }
        //2. 校验验证码
//        String code = (String) session.getAttribute("code");
        //2. 从redis获得code
        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        //3. 不一致,报错
        if (loginForm.getCode() == null || !code.equals(codeEnter)) {
            return Result.fail("验证码错误");
        }

        //4. 一致,根据手机号查询用户
        User user = query().eq("phone", phone).one();
        //5. 不存在,创建用户并保存
        if (user == null) {
            user = createUserWithPhone(phone);
        }
        //6. 保存用户信息到session
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        //6.1 生成一个token作为登陆令牌,作为保存user的key
        String token = UUID.randomUUID().toString();
        //6.2 将user转成hash形式,使用BeanUtil的beanToMap
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fileName, fileValue) -> fileValue.toString()));//自定义转换方式,setFieldValueEditor里面传入一个函数,

        System.out.println(userMap.toString());
        //6.3 保存道redis中,保证map中的key和value都是string类型的
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.SECONDS);
        //6.4 将token返回给客户端
        //7. 再登录校验的interceptor中,如果用户登陆,则刷新expire,延续30mins

        return Result.ok(token);
    }

    @Override
    public Result logout(String token) {
        if (StringUtil.isBlank(token)){
            return Result.fail("请重新登录");
        }
        UserHolder.removeUser();
        Boolean delete = stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        return delete? Result.ok("退出登录"): Result.fail("请重新登陆");
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10));
        boolean save = save(user);
        return user;
    }
}
