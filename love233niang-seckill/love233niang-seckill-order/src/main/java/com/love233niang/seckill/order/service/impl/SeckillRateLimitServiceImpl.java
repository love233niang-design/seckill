package com.love233niang.seckill.order.service.impl;

import com.love233niang.seckill.order.service.SeckillRateLimitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class SeckillRateLimitServiceImpl implements SeckillRateLimitService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final DefaultRedisScript<Long> seckillRateLimitScript;

    public SeckillRateLimitServiceImpl() {
        this.seckillRateLimitScript = new DefaultRedisScript<>();
        this.seckillRateLimitScript.setLocation(new ClassPathResource("lua/seckill_rate_limit.lua"));
        this.seckillRateLimitScript.setResultType(Long.class);
    }

    /**
     * 尝试获取一次请求资格
     *
     * @param limitKey      限流 Key
     * @param maxCount      窗口内允许的最大请求次数
     * @param windowSeconds 窗口时间，单位：秒
     * @return true：允许通过；false：超过阈值
     */
    @Override
    public boolean tryAcquire(String limitKey, long maxCount, long windowSeconds) {
        Long resultCode = stringRedisTemplate.execute(seckillRateLimitScript,
                List.of(limitKey), String.valueOf(maxCount), String.valueOf(windowSeconds));


        if (Objects.isNull(resultCode)) {
            throw new IllegalStateException("执行秒杀接口限流 Lua 脚本失败");
        }

        // 是否允许
        boolean allowed = Objects.equals(resultCode, 1L);

        // 若不允许，记录日志
        if (!allowed) {
            log.warn("==> 秒杀接口触发限流, limitKey: {}, maxCount: {}, windowSeconds: {}",
                    limitKey, maxCount, windowSeconds);
        }

        return allowed;
    }
}
