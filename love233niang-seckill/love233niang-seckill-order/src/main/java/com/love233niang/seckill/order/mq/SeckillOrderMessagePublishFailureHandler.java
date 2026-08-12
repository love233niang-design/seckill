package com.love233niang.seckill.order.mq;

import com.love233niang.seckill.common.mq.MessagePublishFailureHandler;
import com.love233niang.seckill.order.service.SeckillOrderPreDeductCompensationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SeckillOrderMessagePublishFailureHandler implements MessagePublishFailureHandler {
    @Autowired
    private SeckillOrderPreDeductCompensationService seckillOrderPreDeductCompensationService;

    /**
     * Broker 返回 Nack，表示 Broker 未能成功处理这条要发布的消息
     */
    @Override
    public void handleConfirmNack(String messageId, String cause) {
        // 在这里执行 Redis 预扣回补
        seckillOrderPreDeductCompensationService.compensateWhenPublishFailed(messageId,
                "Confirm Nack: " + cause);
    }

    @Override
    public void handleReturned(String messageId, String exchange, String routingKey) {
        // 在这里执行 Redis 预扣回补
        seckillOrderPreDeductCompensationService.compensateWhenPublishFailed(messageId,
                "Return: exchange=" + exchange + ", routingKey=" + routingKey);
    }
}
