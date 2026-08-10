package com.love233niang.seckill.order.service.impl;

import com.love233niang.seckill.common.constant.RedisKeyConstants;
import com.love233niang.seckill.order.enums.SeckillStockDeductResultEnum;
import com.love233niang.seckill.order.service.SeckillStockService;
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
public class SeckillStockServiceImpl implements SeckillStockService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 秒杀库存预扣 Lua 脚本
     */
    private final DefaultRedisScript<Long> seckillPreDeductStockScript;

    SeckillStockServiceImpl() {
        this.seckillPreDeductStockScript = new DefaultRedisScript<>();
        this.seckillPreDeductStockScript.setLocation(new ClassPathResource("lua/seckill_pre_deduct_stock.lua"));
        this.seckillPreDeductStockScript.setResultType(Long.class);
    }

    /**
     * Redis Lua 原子预扣库存
     *
     * @param activityId
     * @param goodsId
     * @param userId
     * @param userOrderTtlSeconds
     * @return
     */
    @Override
    public SeckillStockDeductResultEnum preDeductStock(Long activityId, Long goodsId, Long userId, Long userOrderTtlSeconds) {
        // 构建库存 Redis Key
        String stockKey = RedisKeyConstants.buildSeckillStockKey(activityId, goodsId);

        // 构建用户购买标记 Key
        String userOrderKey = RedisKeyConstants.buildSeckillUserOrderKey(activityId, goodsId, userId);

        // 执行 Lua 脚本
        Long resultCode = stringRedisTemplate.execute(seckillPreDeductStockScript,
                List.of(stockKey, userOrderKey), String.valueOf(userOrderTtlSeconds));

        if (Objects.isNull(resultCode)) {
            throw new IllegalStateException("执行秒杀库存预扣 Lua 脚本失败");
        }
        SeckillStockDeductResultEnum result = SeckillStockDeductResultEnum.getByCode(resultCode);
        if (Objects.isNull(result)) {
            throw new IllegalStateException("秒杀库存预扣 Lua 脚本返回未知结果码：" + resultCode);
        }

        if (Objects.equals(result, SeckillStockDeductResultEnum.STOCK_NOT_PREHEATED)) { // 库存未预热
            log.warn("==> 秒杀库存未预热, key: {}", stockKey);
        } else if (Objects.equals(result, SeckillStockDeductResultEnum.REPEATED_ORDER)) { // 重复参与秒杀
            log.info("==> 重复参与秒杀, userId: {}, activityId: {}, goodsId: {}", userId, activityId, goodsId);
        } else {
            log.info("==> 秒杀库存预扣完成, key: {}, result: {}", stockKey, result.getDescription());
        }
        return result;
    }
}
