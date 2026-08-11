package com.love233niang.seckill.order.mq;

import com.love233niang.seckill.common.mq.MessagePublishFailureHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SeckillOrderMessagePublishFailureHandler implements MessagePublishFailureHandler {

    /**
     * Broker 返回 Nack，表示 Broker 未能成功处理这条要发布的消息
     */
    @Override
    public void handleConfirmNack(String messageId, String cause) {
        // TODO: 后续在这里执行 Redis 预扣回补
    }

    @Override
    public void handleReturned(String messageId, String exchange, String routingKey) {
        // TODO: 后续在这里执行 Redis 预扣回补
    }
}
