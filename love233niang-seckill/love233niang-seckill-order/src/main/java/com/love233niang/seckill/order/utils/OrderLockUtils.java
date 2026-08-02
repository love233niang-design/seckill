package com.love233niang.seckill.order.utils;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class OrderLockUtils {
    /**
     * 锁容器：Key 为 "userId:activityId:goodsId"，Value 为 true
     * 使用 ConcurrentHashMap 保证并发安全
     */
    private final ConcurrentHashMap<String, Boolean> lockMap = new ConcurrentHashMap<>();

    public boolean tryLock(String lockKey) {
        return lockMap.putIfAbsent(lockKey, Boolean.TRUE) == null;
    }

    public void unlock(String lockKey) {
        lockMap.remove(lockKey);
    }
}
