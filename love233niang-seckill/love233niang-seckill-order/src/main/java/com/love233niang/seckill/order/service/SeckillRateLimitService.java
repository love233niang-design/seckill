package com.love233niang.seckill.order.service;

public interface SeckillRateLimitService {

    /**
     * 尝试获取一次请求资格
     *
     * @param limitKey      限流 Key
     * @param maxCount      窗口内允许的最大请求次数
     * @param windowSeconds 窗口时间，单位：秒
     * @return true：允许通过；false：超过阈值
     */
    boolean tryAcquire(String limitKey, long maxCount, long windowSeconds);

}

