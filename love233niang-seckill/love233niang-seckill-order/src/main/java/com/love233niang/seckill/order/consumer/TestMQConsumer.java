package com.love233niang.seckill.order.consumer;

import com.love233niang.seckill.common.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TestMQConsumer {
    /**
     * 监听测试队列，收到消息后打印日志
     *
     * @param message
     */
    @RabbitListener(queues = RabbitMQConfig.TEST_QUEUE)
    public void consume(String message) {
        log.info("## 收到测试消息: {}", message);
    }
}
