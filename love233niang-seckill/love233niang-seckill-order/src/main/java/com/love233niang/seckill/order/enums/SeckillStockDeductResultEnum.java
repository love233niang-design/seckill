package com.love233niang.seckill.order.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SeckillStockDeductResultEnum {

    SUCCESS(1L, "库存预扣成功"),
    SOLD_OUT(0L, "秒杀商品已售罄"),
    STOCK_NOT_PREHEATED(-1L, "秒杀库存未预热");

    /**
     * Lua 脚本返回码
     */
    private final Long code;

    /**
     * 返回码说明
     */
    private final String description;

    /**
     * 根据 Lua 返回码获取预扣结果
     */
    public static SeckillStockDeductResultEnum getByCode(Long code) {
        for (SeckillStockDeductResultEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
