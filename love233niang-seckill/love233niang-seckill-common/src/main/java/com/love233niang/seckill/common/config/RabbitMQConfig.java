package com.love233niang.seckill.common.config;


import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;


@Configuration
public class RabbitMQConfig {
    /**
     * 测试交换机名称
     */
    public static final String TEST_EXCHANGE = "seckill.test.exchange";

    /**
     * 测试队列名称
     */
    public static final String TEST_QUEUE = "seckill.test.queue";

    /**
     * 测试路由键
     */
    public static final String TEST_ROUTING_KEY = "seckill.test.routing.key";

    /**
     * 自定义消息转换器使用 Jakson 序列化（Json）
     *
     * @return
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }


    /**
     * 测试交换机（Direct 类型，持久化），仅在 dev 环境创建
     */
    @Bean
    @Profile("dev")
    public DirectExchange testExchange() {
        // 参数：名称、是否持久化(durable)、是否自动删除(autoDelete)
        return new DirectExchange(TEST_EXCHANGE, true, false);
    }

    /**
     * 测试队列（持久化），仅在 dev 环境创建
     */
    @Bean
    @Profile("dev")
    public Queue testQueue() {
        // 参数：名称、是否持久化(durable)
        return new Queue(TEST_QUEUE, true);
    }

    /**
     * 将测试队列绑定到测试交换机，指定路由键，仅在 dev 环境创建
     */
    @Bean
    @Profile("dev")
    public Binding testBinding() {
        // 参数：队列、交换机、路由键
        return BindingBuilder.bind(testQueue())
                .to(testExchange())
                .with(TEST_ROUTING_KEY);
    }
}
