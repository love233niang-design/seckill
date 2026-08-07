package com.love233niang.seckill.order.consumer;

import com.love233niang.seckill.common.config.RabbitMQConfig;
import com.love233niang.seckill.order.model.dto.SeckillOrderMqDTO;
import com.love233niang.seckill.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SeckillOrderConsumer {
    @Autowired
    private OrderService orderService;

    @RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE, concurrency = "5-10")
    public void consume(SeckillOrderMqDTO message) {
        log.info("## 收到秒杀订单消息: {}", message);
        orderService.createSeckillOrder(message);
    }
}
