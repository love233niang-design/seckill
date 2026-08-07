package com.love233niang.seckill.order.service;

import com.love233niang.seckill.common.utils.Response;
import com.love233niang.seckill.order.model.dto.SeckillOrderMqDTO;
import com.love233niang.seckill.order.model.vo.DoSeckillReqVO;
import com.love233niang.seckill.order.model.vo.DoSeckillRspVO;

/**
 * @Author: hq
 * @Date: 2026/5/8 10:00
 * @Version: v1.0.0
 * @Description: 订单模块业务
 **/
public interface OrderService {

    /**
     * 秒杀下单
     *
     * @param reqVO
     * @return
     */
    Response<DoSeckillRspVO> doSeckill(DoSeckillReqVO reqVO);


    /**
     * 异步消费秒杀下单消息：扣减库存 + 创建订单
     *
     * @param message
     */
    void createSeckillOrder(SeckillOrderMqDTO message);
}
