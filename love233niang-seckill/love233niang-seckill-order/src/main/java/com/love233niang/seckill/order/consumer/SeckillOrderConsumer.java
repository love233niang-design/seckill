package com.love233niang.seckill.order.consumer;

import com.love233niang.seckill.common.config.RabbitMQConfig;
import com.love233niang.seckill.order.model.dto.SeckillOrderMqDTO;
import com.love233niang.seckill.order.service.OrderService;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class SeckillOrderConsumer {
    @Autowired
    private OrderService orderService;

    @RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE, concurrency = "5-10")
    public void consume(SeckillOrderMqDTO seckillOrderMqDTO, Channel channel, Message message) throws IOException {
        // deliveryTag 是 RabbitMQ 给当前投递消息分配的唯一编号，手动 ACK/NACK 时必须带上它
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        log.info("## 收到秒杀订单消息: {}", seckillOrderMqDTO);
        try {
            orderService.createSeckillOrder(seckillOrderMqDTO);
        } catch (Exception e) {
            log.error("## 秒杀订单消息处理出现系统异常，进入死信队列, orderNo: {}, deliveryTag: {}",
                    seckillOrderMqDTO.getOrderNo(), deliveryTag, e);
            // requeue=false：不重新入主队列，交给死信队列兜底，避免异常消息无限重试
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        // 手动 ACK，RabbitMQ 才会将该消息从队列中删除
        channel.basicAck(deliveryTag, false);
        log.info("==> 秒杀订单消息已 ACK, orderNo: {}, deliveryTag: {}", seckillOrderMqDTO.getOrderNo(), deliveryTag);
    }
}
