package com.love233niang.seckill.order.service;

import com.love233niang.seckill.order.enums.SeckillStockDeductResultEnum;

/**
 * @Author: hq
 * @Date: 2026/7/20 20:21
 * @Version: v1.0.0
 * @Description: 秒杀库存服务
 **/
public interface SeckillStockService {

    /**
     * Redis Lua 原子预扣库存
     *
     * @param activityId
     * @param goodsId
     * @param userId
     * @param userOrderTtlSeconds
     * @return
     */
    SeckillStockDeductResultEnum preDeductStock(Long activityId, Long goodsId,
                                                Long userId, Long userOrderTtlSeconds,
                                                Long activityBeginTimeMillis, Long activityEndTimeMillis);
}

