package com.love233niang.seckill.order.service.impl;

import com.love233niang.seckill.order.model.vo.FindSeckillOrderResultRspVO;
import com.love233niang.seckill.order.service.SeckillOrderResultNotifyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SeckillOrderResultNotifyServiceImpl implements SeckillOrderResultNotifyService {
    /**
     * SSE 连接超时时间：60 秒
     */
    private static final long SSE_TIMEOUT_MILLIS = 60000L;

    /**
     * 存储用户维度的 SSE 连接
     */
    private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    /**
     * 订阅秒杀订单处理结果
     *
     * @param userId
     * @param orderNo
     * @return
     */
    @Override
    public SseEmitter subscribe(Long userId, String orderNo) {
        // 创建唯一标识
        String emitterKey = userId + ":" + orderNo;

        // 创建 SSE 连接
        SseEmitter sseEmitter = new SseEmitter(SSE_TIMEOUT_MILLIS);

        // 存储 SSE 连接
        emitterMap.put(emitterKey, sseEmitter);

        // 浏览器断开、连接超时或推送异常时，要清理一下连接，避免内存泄漏
        sseEmitter.onCompletion(() -> emitterMap.remove(emitterKey));
        sseEmitter.onTimeout(() -> emitterMap.remove(emitterKey));
        sseEmitter.onError(e -> emitterMap.remove(emitterKey));

        log.info("==> 秒杀订单结果 SSE 订阅成功, userId: {}, orderNo: {}", userId, orderNo);
        return sseEmitter;
    }

    /**
     * 推送秒杀订单处理结果
     *
     * @param userId
     * @param result
     */
    @Override
    public void notifyOrderResult(Long userId, FindSeckillOrderResultRspVO result) {
        // 创建唯一标识
        String emitterKey = userId + ":" + result.getOrderNo();

        // 获取 SSE 连接并删除
        SseEmitter sseEmitter = emitterMap.remove(emitterKey);
        if (Objects.isNull(sseEmitter)) {
            log.info("==> 秒杀订单结果 SSE 连接不存在, 跳过推送, userId: {}, orderNo: {}", userId, result.getOrderNo());
            return;
        }

        try {
            // 推送结果
            sseEmitter.send(SseEmitter.event()
                    .name("seckill-order-result")
                    .data(result));

            // 关闭 SSE 连接
            sseEmitter.complete();

            log.info("==> 秒杀订单结果 SSE 推送成功, userId: {}, orderNo: {}, status: {}", userId, result.getOrderNo(), result.getStatus());
        } catch (IOException e) {
            log.error("==> 秒杀订单结果 SSE 推送失败, userId: {}, orderNo: {}", userId, result.getOrderNo(), e);
        }

    }
}
