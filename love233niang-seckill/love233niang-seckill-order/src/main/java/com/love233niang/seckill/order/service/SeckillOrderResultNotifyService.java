package com.love233niang.seckill.order.service;

import com.love233niang.seckill.order.model.vo.FindSeckillOrderResultRspVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * @Author: hq
 * @Date: 2026/7/14 18:15
 * @Version: v1.0.0
 * @Description: 秒杀订单结果通知
 **/
public interface SeckillOrderResultNotifyService {

    /**
     * 订阅秒杀订单处理结果
     *
     * @param userId
     * @param orderNo
     * @return
     */
    SseEmitter subscribe(Long userId, String orderNo);

    /**
     * 推送秒杀订单处理结果
     * 
     * @param userId
     * @param result
     */
    void notifyOrderResult(Long userId, FindSeckillOrderResultRspVO result);

}

