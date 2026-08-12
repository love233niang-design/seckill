package com.love233niang.seckill.order.service.impl;

import com.love233niang.seckill.common.constant.RedisKeyConstants;
import com.love233niang.seckill.common.utils.JsonUtils;
import com.love233niang.seckill.order.enums.SeckillStockCompensationResultEnum;
import com.love233niang.seckill.order.enums.SeckillStockDeductResultEnum;
import com.love233niang.seckill.order.model.dto.SeckillOrderMqDTO;
import com.love233niang.seckill.order.service.SeckillStockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 秒杀库存服务实现类
 */
@Service
@Slf4j
public class SeckillStockServiceImpl implements SeckillStockService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 秒杀库存预扣 Lua 脚本
     */
    private final DefaultRedisScript<Long> seckillPreDeductStockScript;

    /**
     * 秒杀库存预扣回补 Lua 脚本
     */
    private final DefaultRedisScript<Long> seckillCompensatePreDeductStockScript;

    SeckillStockServiceImpl() {
        this.seckillPreDeductStockScript = new DefaultRedisScript<>();
        this.seckillPreDeductStockScript.setLocation(new ClassPathResource("lua/seckill_pre_deduct_stock.lua"));
        this.seckillPreDeductStockScript.setResultType(Long.class);

        this.seckillCompensatePreDeductStockScript = new DefaultRedisScript<>();
        this.seckillCompensatePreDeductStockScript.setLocation(new ClassPathResource("lua/seckill_compensate_pre_deduct_stock.lua"));
        this.seckillCompensatePreDeductStockScript.setResultType(Long.class);
    }

    /**
     * 秒杀库存预扣
     *
     * @param seckillOrderMqDTO
     * @param userOrderTtlSeconds
     * @param activityBeginTimeMillis
     * @param activityEndTimeMillis
     * @return
     */
    @Override
    public SeckillStockDeductResultEnum preDeductStock(SeckillOrderMqDTO seckillOrderMqDTO,
                                                       Long userOrderTtlSeconds,
                                                       Long activityBeginTimeMillis,
                                                       Long activityEndTimeMillis) {
        Long activityId = seckillOrderMqDTO.getActivityId();
        Long goodsId = seckillOrderMqDTO.getGoodsId();
        Long userId = seckillOrderMqDTO.getUserId();
        // 构建库存 Redis Key
        String stockKey = RedisKeyConstants.buildSeckillStockKey(activityId, goodsId);

        // 构建用户购买标记 Key
        String userOrderKey = RedisKeyConstants.buildSeckillUserOrderKey(activityId, goodsId, userId);

        // 构建待回补上下文 Key
        String compensationKey = RedisKeyConstants.buildSeckillOrderCompensationKey(seckillOrderMqDTO.getOrderNo());

        // 执行 Lua 脚本
        Long resultCode = stringRedisTemplate.execute(seckillPreDeductStockScript,
                List.of(stockKey, userOrderKey, compensationKey), String.valueOf(userOrderTtlSeconds),
                String.valueOf(activityBeginTimeMillis), String.valueOf(activityEndTimeMillis),
                JsonUtils.toJsonString(seckillOrderMqDTO), seckillOrderMqDTO.getOrderNo());

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

    /**
     * Redis Lua 原子回补秒杀库存，并删除用户购买标记
     *
     * @param activityId
     * @param goodsId
     * @param userId
     * @param orderNo
     * @return
     */
    @Override
    public SeckillStockCompensationResultEnum compensatePreDeductStock(Long activityId, Long goodsId, Long userId, String orderNo) {
        // 构建库存 Redis Key
        String stockKey = RedisKeyConstants.buildSeckillStockKey(activityId, goodsId);
        // 构建用户购买标记 Redis Key
        String userOrderKey = RedisKeyConstants.buildSeckillUserOrderKey(activityId, goodsId, userId);

        // 执行 lua 脚本
        Long resultCode = stringRedisTemplate.execute(
                seckillCompensatePreDeductStockScript,
                List.of(stockKey, userOrderKey),
                orderNo
        );

        if (Objects.isNull(resultCode)) {
            throw new IllegalStateException("执行秒杀库存预扣回补 Lua 脚本失败");
        }
        // 获取返回值对应枚举
        SeckillStockCompensationResultEnum result = SeckillStockCompensationResultEnum.getByCode(resultCode);
        if (Objects.isNull(result)) {
            throw new IllegalStateException("秒杀库存预扣回补 Lua 脚本返回未知结果码：" + resultCode);
        }

        log.info("==> 秒杀库存回补完成, stockKey: {}, userOrderKey: {}, result: {}",
                stockKey, userOrderKey, result.getDescription());
        return result;
    }
}
