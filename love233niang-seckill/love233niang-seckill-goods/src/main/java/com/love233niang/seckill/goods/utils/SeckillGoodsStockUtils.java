package com.love233niang.seckill.goods.utils;

import cn.hutool.core.util.StrUtil;

public class SeckillGoodsStockUtils {
    private SeckillGoodsStockUtils() {

    }

    /**
     * 计算页面展示库存
     *
     * @param stockValue    Redis 秒杀库存值
     * @param fallbackStock 兜底库存值
     * @return 页面展示库存
     */
    public static Integer resolveDisplayStock(String stockValue, Integer fallbackStock) {
        if ((StrUtil.isBlank(stockValue))) {
            return fallbackStock;
        }


        try {
            return Math.max(Integer.parseInt(stockValue), 0);
        } catch (NumberFormatException e) {
            return fallbackStock;
        }
    }
}
