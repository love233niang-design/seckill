package com.love233niang.seckill.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.love233niang.seckill.common.domain.dataobject.UserDO;
import com.love233niang.seckill.common.domain.mapper.UserDOMapper;
import com.love233niang.seckill.common.enums.ResponseCodeEnum;
import com.love233niang.seckill.common.exception.BizException;
import com.love233niang.seckill.common.utils.Response;
import com.love233niang.seckill.user.enums.LoginTypeEnum;
import com.love233niang.seckill.user.enums.UserStatusEnum;
import com.love233niang.seckill.user.enums.VerifyCodeTypeEnum;
import com.love233niang.seckill.user.model.vo.LoginUserReqVO;
import com.love233niang.seckill.user.model.vo.LoginUserRspVO;
import com.love233niang.seckill.user.model.vo.RegisterUserReqVO;
import com.love233niang.seckill.user.model.vo.SendVerifyCodeReqVO;
import com.love233niang.seckill.user.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class UserServiceImpl implements UserService {
    @Autowired
    private UserDOMapper userDOMapper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource(name = "bizExecutor")
    private Executor bizExecutor;

    // Redis 中验证码的 Key 前缀
    private static final String VERIFY_CODE_KEY_PREFIX = "verify_code:";
    // Redis 中发送频率限制的 Key 前缀
    private static final String VERIFY_CODE_LIMIT_KEY_PREFIX = "verify_code_limit:";
    // 验证码过期时间（分钟）
    private static final Long VERIFY_CODE_EXPIRE_MINUTES = 5L;
    // 发送频率限制时间（秒）
    private static final Long VERIFY_CODE_LIMIT_SECONDS = 60L;
    // Redis 中每日发送次数限制的 Key 前缀
    private static final String VERIFY_CODE_DAILY_LIMIT_KEY_PREFIX = "verify_code_daily:";
    // 每日发送次数上限
    private static final Integer VERIFY_CODE_DAILY_LIMIT = 10;
    // Redis 中登录失败次数的 Key 前缀
    private static final String LOGIN_FAIL_COUNT_KEY_PREFIX = "login_fail_count:";
    // 登录失败次数上限（超过此值则临时锁定账号）
    private static final Integer LOGIN_FAIL_MAX_COUNT = 5;
    // 账号临时锁定时间（分钟）
    private static final Long LOGIN_LOCK_MINUTES = 30L;


    /**
     * 验证码校验 Lua 脚本
     */
    private final DefaultRedisScript<Long> checkAndDeleteVerifyCodeScript;

    /**
     * 构造函数：初始化 Lua 脚本
     */
    public UserServiceImpl() {
        checkAndDeleteVerifyCodeScript = new DefaultRedisScript<>();
        // 从 classpath 的 lua 目录下加载 Lua 脚本文件
        checkAndDeleteVerifyCodeScript.setLocation(new ClassPathResource("lua/check_and_delete_verify_code.lua"));
        // 指定脚本返回值类型
        checkAndDeleteVerifyCodeScript.setResultType(Long.class);
    }

    /**
     * 用户注册
     *
     * @param registerUserReqVO
     * @return
     */
    @Override
    public Response<?> register(RegisterUserReqVO registerUserReqVO) {
        String mobile = registerUserReqVO.getMobile();
        String password = registerUserReqVO.getPassword();
        String verifyCode = registerUserReqVO.getVerifyCode();

        // 校验验证码
        checkVerifyCode(verifyCode, mobile, VerifyCodeTypeEnum.REGISTER.getPurpose());

        // 校验手机号是否已经注册
        Long existUserId = userDOMapper.selectIdByMobile(mobile);
        if (Objects.nonNull(existUserId)) {
            throw new BizException(ResponseCodeEnum.USER_MOBILE_EXISTS);
        }

        // 密码加密 ByCrypt 算法
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        String encodedPassword = passwordEncoder.encode(password);

        // 构建用户实体，插入数据库
        UserDO userDO = UserDO.builder()
                .mobile(mobile)
                .password(encodedPassword)
                .nickname(generateNickname())
                .status(UserStatusEnum.ENABLED.getCode())
                .build();

        userDOMapper.insertSelective(userDO);
        log.info("==> 用户注册成功, mobile: {}", mobile);

        return Response.success();
    }

    /**
     * 用户登录
     *
     * @param loginUserReqVO
     * @return
     */
    @Override
    public Response<LoginUserRspVO> login(LoginUserReqVO loginUserReqVO) {
        String mobile = loginUserReqVO.getMobile();
        Integer type = loginUserReqVO.getType();

        // 根据手机号查询用户
        UserDO userDO = userDOMapper.selectByMobile(mobile);

        // 判断用户是否存在
        if (Objects.isNull(userDO)) {
            throw new BizException(ResponseCodeEnum.USER_MOBILE_NOT_REGISTERED);
        }

        // 校验用户状态（是否被禁用， 放在身份验证之前，避免被禁用账号还执行耗时的密码校验）
        if (Objects.equals(userDO.getStatus(), UserStatusEnum.DISABLED.getCode())) {
            throw new BizException(ResponseCodeEnum.USER_STATUS_DISABLED);
        }

        // 根据登录类型，进行身份验证
        if (Objects.equals(type, LoginTypeEnum.PASSWORD.getCode())) {
            // 检查登录失败次数
            checkLoginFailLimit(mobile);

            // 密码登录：校验密码是否正确
            checkPassword(loginUserReqVO.getPassword(), userDO.getPassword(), mobile);
        } else {
            // 验证码登录：校验验证码
            checkVerifyCode(loginUserReqVO.getVerifyCode(), mobile, VerifyCodeTypeEnum.LOGIN.getPurpose());
        }

        // 调用 SaToken 执行登录，传入 用户 ID
        StpUtil.login(userDO.getId());

        // 获取 SaToken 登录的 token
        String token = StpUtil.getTokenValue();

        // 构建反参对象
        LoginUserRspVO loginUserRspVO = LoginUserRspVO.builder()
                .token(token)
                .userInfo(LoginUserRspVO.UserInfo.builder()
                        .id(userDO.getId())
                        .nickname(userDO.getNickname())
                        .avatar(userDO.getAvatar())
                        .build())
                .build();

        log.info("==> 用户登录成功, userId: {}, mobile: {}", userDO.getId(), mobile);

        return Response.success(loginUserRspVO);
    }

    /**
     * 发送验证码
     *
     * @param sendVerifyCodeReqVO
     * @return
     */
    @Override
    public Response<?> sendVerifyCode(SendVerifyCodeReqVO sendVerifyCodeReqVO) {
        String mobile = sendVerifyCodeReqVO.getMobile();
        Integer type = sendVerifyCodeReqVO.getType();

        // 判断验证码类型是否合法
        VerifyCodeTypeEnum verifyCodeType = VerifyCodeTypeEnum.valueOf(type);
        if (Objects.isNull(verifyCodeType)) {
            throw new BizException(ResponseCodeEnum.VERIFY_CODE_TYPE_ERROR);
        }

        // 发送频率限制：检查是否在 60 秒内重复发送
        String limitKey = VERIFY_CODE_LIMIT_KEY_PREFIX + verifyCodeType.getPurpose() + ":" + mobile;

        if (redisTemplate.hasKey(limitKey)) {
            throw new BizException(ResponseCodeEnum.VERIFY_CODE_SEND_TOO_FREQUENT);
        }

        // 每日发送次数限制：同一手机号、同一场景，每天最多发送10 条
        String dailyLimitKey = VERIFY_CODE_DAILY_LIMIT_KEY_PREFIX + verifyCodeType.getPurpose()
                + ":" + mobile + ":" + LocalDate.now();

        // 发送次数 +1
        Long dailyCount = redisTemplate.opsForValue().increment(dailyLimitKey);

        // 首次设置缓存时，计算当天结束的剩余秒数，作为 key 的 ttl 过期时间
        if (Objects.nonNull(dailyCount) && dailyCount == 1) {
            // 计算当前时间。到第二天凌晨之间还剩下多少秒
            long secondsUntilMidnight = Duration.between(
                    LocalDateTime.now(),
                    LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.MIDNIGHT)
            ).getSeconds();
            // 设置过期时间
            redisTemplate.expire(dailyLimitKey, secondsUntilMidnight, TimeUnit.SECONDS);
        }

        // 如果已经超过十条， 抛出异常
        if (Objects.nonNull(dailyCount) && dailyCount > VERIFY_CODE_DAILY_LIMIT) {
            throw new BizException(ResponseCodeEnum.VERIFY_CODE_DAILY_LIMIT_EXCEEDED);
        }
        // 生成 6 位随机数字验证码
        String verifyCode = RandomUtil.randomNumbers(6);

        // 通过 Pipeline 通道，批量写入 Redis（频率限制 Key + 验证码），减少网络往返，降低部分失败的风险
        String redisKey = VERIFY_CODE_KEY_PREFIX + verifyCodeType.getPurpose() + ":" + mobile;
        redisTemplate.executePipelined(new SessionCallback<Void>() {
            @Override
            public Void execute(RedisOperations operations) {
                // 先写频率限制 Key（60 秒 TTL）
                operations.opsForValue().set(limitKey, "1", VERIFY_CODE_LIMIT_SECONDS, TimeUnit.SECONDS);
                // 再写验证码 Key（5 分钟 TTL）
                operations.opsForValue().set(redisKey, verifyCode, VERIFY_CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
                return null;
            }
        });
        bizExecutor.execute(() -> sendSms(mobile, verifyCode));
        return Response.success();
    }

    /**
     * 发送短信验证码（异步执行，由线程池调度）
     *
     * @param mobile     手机号
     * @param verifyCode 验证码
     */
    private void sendSms(String mobile, String verifyCode) {
        try {
            // TODO: 调用短信服务商 API 发送验证码

            // 开发阶段通过日志打印验证码，方便调试
            log.info("==> 验证码发送成功, mobile: {}, verifyCode: {}", mobile, verifyCode);
        } catch (Exception e) {
            log.error("==> 验证码发送失败, mobile: {}, verifyCode: {}", mobile, verifyCode, e);
        }
    }

    /**
     * 校验密码
     *
     * @param rawPassword     明文密码
     * @param encodedPassword 加密后的密码
     * @param mobile          手机号（用于构建 Redis Key）
     */
    private void checkPassword(String rawPassword, String encodedPassword, String mobile) {
        // 密码不能为空
        if (StrUtil.isBlank(rawPassword)) {
            addLoginFailCount(mobile);
            throw new BizException(ResponseCodeEnum.USER_PASSWORD_ERROR);
        }

        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        // 使用 BCrypt 校验明文密码和密文密码是否匹配
        boolean matches = passwordEncoder.matches(rawPassword, encodedPassword);
        if (!matches) {
            addLoginFailCount(mobile);
            throw new BizException(ResponseCodeEnum.USER_PASSWORD_ERROR);
        }

        // 密码校验成功，清除登录失败次数
        String failCountKey = LOGIN_FAIL_COUNT_KEY_PREFIX + mobile;
        redisTemplate.delete(failCountKey);
    }

    /**
     * 校验验证码
     *
     * @param verifyCode 验证码
     */
    private void checkVerifyCode(String verifyCode, String mobile, String purpose) {
        // 验证码不能为空
        if (StrUtil.isBlank(verifyCode)) {
            throw new BizException(ResponseCodeEnum.USER_VERIFY_CODE_ERROR);
        }

        // 构建 Redis Key
        String redisKey = VERIFY_CODE_KEY_PREFIX + purpose + ":" + mobile;

        // 执行 Lua 脚本：原子性地比对验证码并删除（匹配返回 1; 不匹配或 Key 不存在返回 0）
        Long result = redisTemplate.execute(
                checkAndDeleteVerifyCodeScript, // Lua 脚本对象
                Collections.singletonList(redisKey), // Redis key 列表
                verifyCode); // 可变参数

        // 验证码错误或已过期
        if (result == null || result == 0) {
            throw new BizException(ResponseCodeEnum.USER_VERIFY_CODE_ERROR);
        }

        // 能走到这里，说明 result == 1， 验证码比对成功，并且验证码已被删除

    }

    /**
     * 生成随机昵称
     * 格式：用户 + 6 位随机数字，如：用户382910
     *
     * @return 昵称
     */
    private String generateNickname() {
        return "用户" + RandomUtil.randomNumbers(6);
    }

    /**
     * 检查登录失败次数是否超限
     *
     * @param mobile 手机号
     */
    private void checkLoginFailLimit(String mobile) {
        // 构建 Redis Key
        String failCountKey = LOGIN_FAIL_COUNT_KEY_PREFIX + mobile;

        // 查询 Redis 缓存中的计数
        Integer failCount = (Integer) redisTemplate.opsForValue().get(failCountKey);

        // 判断登录失败次数是否超过上限
        if (Objects.nonNull(failCount) && failCount >= LOGIN_FAIL_MAX_COUNT) {
            throw new BizException(ResponseCodeEnum.LOGIN_FAIL_TOO_MANY);
        }
    }

    /**
     * 累加登录失败次数
     *
     * @param mobile 手机号
     */
    private void addLoginFailCount(String mobile) {
        // 构建 Redis Key
        String failCountKey = LOGIN_FAIL_COUNT_KEY_PREFIX + mobile;

        // 累加登录失败次数
        Long failCount = redisTemplate.opsForValue().increment(failCountKey);

        // 如果是第一次添加缓存，需要设置过期时间
        if (Objects.nonNull(failCount) && failCount == 5) {
            redisTemplate.expire(failCountKey, LOGIN_LOCK_MINUTES, TimeUnit.MINUTES);
        }
    }
}
