package com.love233niang.seckill.order.controller;

import com.love233niang.seckill.common.config.RabbitMQConfig;
import com.love233niang.seckill.common.utils.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;


@Slf4j
@RestController
@RequestMapping("/test/mq")
public class TestMqController {
    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送测试消息到 RabbitMQ
     *
     * @return
     */
    @GetMapping("/send")
    public Response<String> sendTestMessage() {
        String message = "Hello RabbitMQ! 发送时间: " + LocalDateTime.now();

        // 通过 RabbitTemplate 发送消息：指定交换机、路由键、消息内容
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.TEST_EXCHANGE,
                RabbitMQConfig.TEST_ROUTING_KEY,
                message
        );

        log.info("==> 测试消息发送成功: {}", message);
        return Response.success("消息发送成功");
    }
}
