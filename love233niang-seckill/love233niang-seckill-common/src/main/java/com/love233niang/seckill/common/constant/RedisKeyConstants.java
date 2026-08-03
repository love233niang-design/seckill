package com.love233niang.seckill.common.constant;

public class RedisKeyConstants {
    /**
     * 商品列表缓存 Key 前缀
     *
     * 完整格式：seckill:goods:list:{activityId}
     */
    public static final String GOODS_LIST_PREFIX = "seckill:goods:list:";

    /**
     * 商品列表缓存过期时间（单位：分钟）
     */
    public static final long GOODS_LIST_TTL_MINUTES = 30;
}
