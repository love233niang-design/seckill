package com.love233niang.seckill.order.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author: hq
 * @Date: 2026/5/8 18:28
 * @Version: v1.0.0
 * @Description: 订单状态枚举
 **/
@Getter
@AllArgsConstructor
public enum OrderStatusEnum {

    PROCESSING(-1, "处理中"),
    PENDING_PAYMENT(0, "待支付"),
    PENDING_SHIPMENT(1, "待发货"),
    SHIPPED(2, "已发货"),
    RECEIVED(3, "已收货"),
    REFUNDED(4, "已退款"),
    CANCELLED(5, "已取消"),
    CLOSED(6, "已关闭");

    /**
     * 状态值
     */
    private final Integer status;

    /**
     * 状态描述
     */
    private final String description;
}

