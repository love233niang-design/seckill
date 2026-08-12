package com.love233niang.seckill.order.service;

import com.love233niang.seckill.order.enums.SeckillStockCompensationResultEnum;
import com.love233niang.seckill.order.enums.SeckillStockDeductResultEnum;
import com.love233niang.seckill.order.model.dto.SeckillOrderMqDTO;

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
     * @param seckillOrderMqDTO
     * @param userOrderTtlSeconds
     * @param activityBeginTimeMillis
     * @param activityEndTimeMillis
     * @return
     */
    SeckillStockDeductResultEnum preDeductStock(SeckillOrderMqDTO seckillOrderMqDTO,
                                                Long userOrderTtlSeconds,
                                                Long activityBeginTimeMillis,
                                                Long activityEndTimeMillis);

    /**
     * Redis Lua 原子回补秒杀库存，并删除用户购买标记
     */
    SeckillStockCompensationResultEnum compensatePreDeductStock(Long activityId,
                                                                Long goodsId,
                                                                Long userId,
                                                                String orderNo);
}

