package com.love233niang.seckill.order.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.Hutool;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.love233niang.seckill.common.config.RabbitMQConfig;
import com.love233niang.seckill.common.constant.RedisKeyConstants;
import com.love233niang.seckill.common.domain.dataobject.GoodsDO;
import com.love233niang.seckill.common.domain.dataobject.SeckillActivityDO;
import com.love233niang.seckill.common.domain.dataobject.SeckillGoodsDO;
import com.love233niang.seckill.common.domain.dataobject.SeckillOrderDO;
import com.love233niang.seckill.common.domain.mapper.*;
import com.love233niang.seckill.common.enums.ResponseCodeEnum;
import com.love233niang.seckill.common.exception.BizException;
import com.love233niang.seckill.common.model.dto.SeckillActivityGoodsMetaDTO;
import com.love233niang.seckill.common.utils.DateTimeUtils;
import com.love233niang.seckill.common.utils.JsonUtils;
import com.love233niang.seckill.common.utils.Response;
import com.love233niang.seckill.order.enums.OrderStatusEnum;
import com.love233niang.seckill.order.enums.SeckillStockDeductResultEnum;
import com.love233niang.seckill.order.model.dto.SeckillOrderMqDTO;
import com.love233niang.seckill.order.model.vo.DoSeckillReqVO;
import com.love233niang.seckill.order.model.vo.DoSeckillRspVO;
import com.love233niang.seckill.order.model.vo.FindSeckillOrderResultReqVO;
import com.love233niang.seckill.order.model.vo.FindSeckillOrderResultRspVO;
import com.love233niang.seckill.order.mq.SeckillOrderMessageSender;
import com.love233niang.seckill.order.service.OrderService;
import com.love233niang.seckill.order.service.SeckillOrderResultNotifyService;
import com.love233niang.seckill.order.service.SeckillStockService;
import com.love233niang.seckill.order.utils.OrderLockUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀订单服务实现类
 */
@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    @Autowired
    private SeckillActivityDOMapper seckillActivityDOMapper;
    @Autowired
    private SeckillGoodsDOMapper seckillGoodsDOMapper;
    @Autowired
    private SeckillOrderDOMapper seckillOrderDOMapper;
    @Autowired
    private GoodsDOMapper goodsDOMapper;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private OrderLockUtils orderLockUtils;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private SeckillOrderMessageSender seckillOrderMessageSender;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private SeckillOrderResultNotifyService seckillOrderResultNotifyService;
    @Autowired
    private SeckillStockService seckillStockService;

    /**
     * 秒杀下单 (生产者)
     *
     * @param reqVO
     * @return
     */
    @Override
    public Response<DoSeckillRspVO> doSeckill(DoSeckillReqVO reqVO) {
        // 活动 ID
        Long activityId = reqVO.getActivityId();
        // 商品 ID
        Long goodsId = reqVO.getGoodsId();

        // 记录请求时间
        LocalDateTime now = LocalDateTime.now();

        // 1. 获取当前登录用户 ID
        long userId = StpUtil.getLoginIdAsLong();
        log.info("==> 当前登录用户 ID: {}", userId);

        // 从 Redis 中查询秒杀商品元数据
        String redisKey = RedisKeyConstants.buildSeckillActivityGoodsMetaKey(activityId, goodsId);

        String cachedValue = stringRedisTemplate.opsForValue().get(redisKey);

        if (StrUtil.isBlank(cachedValue)) {
            log.error("==> 秒杀下单入口元数据未预热, key: {}", redisKey);
            throw new IllegalStateException("秒杀下单入口元数据未预热，请先预热活动");
        }

        // 转换 Json 字符串为实体类
        SeckillActivityGoodsMetaDTO activityGoodsMetaDTO = JsonUtils.parseObject(cachedValue, SeckillActivityGoodsMetaDTO.class);

        // 根据活动结束时间，来计算用户购买标记缓存的 TTL，覆盖整个秒杀活动周期
        Long userOrderTtlSeconds = RedisKeyConstants.calculateTtlSeconds(activityGoodsMetaDTO.getEndTime());

        // 由于业务需要，避免售罄标记, 抢在活动状态校验、一人一单之前返回，不然提示信息，对用户不友好
        // 校验活动开始、结束时间
        if (now.isBefore(activityGoodsMetaDTO.getBeginTime())) {
            throw new BizException(ResponseCodeEnum.SECKILL_ACTIVITY_NOT_STARTED);
        }
        if (!now.isBefore(activityGoodsMetaDTO.getEndTime())) {
            throw new BizException(ResponseCodeEnum.SECKILL_ACTIVITY_ENDED);
        }

        // 如果用户已经参与过本场秒杀时，优先返回重复参与，而不是被售罄标记拦截
        String userOrderKey = RedisKeyConstants.buildSeckillUserOrderKey(activityId, goodsId, userId);
        if (stringRedisTemplate.hasKey(userOrderKey)) {
            throw new BizException(ResponseCodeEnum.SECKILL_ORDER_DUPLICATE);
        }

        // 秒杀商品已经售罄时，直接快速失败，不再继续生成 orderNo 和执行 Lua 脚本
        String soldOutKey = RedisKeyConstants.buildSeckillSoldOutKey(activityId, goodsId);
        if (stringRedisTemplate.hasKey(soldOutKey)) {
            log.info("==> 秒杀商品已售罄，命中售罄标记快速失败, key: {}", soldOutKey);
            throw new BizException(ResponseCodeEnum.SECKILL_GOODS_SOLD_OUT);
        }

        // 使用 Hutool 提供的工具方法，通过雪花算法生成订单号
        String orderNo = IdUtil.getSnowflakeNextIdStr();


        // 构建消息体
        SeckillOrderMqDTO seckillOrderMqDTO = SeckillOrderMqDTO.builder()
                .userId(userId)
                .activityId(activityId)
                .seckillGoodsId(activityGoodsMetaDTO.getSeckillGoodsId())
                .goodsName(activityGoodsMetaDTO.getGoodsName())
                .goodsImg(activityGoodsMetaDTO.getGoodsImg())
                .seckillPrice(activityGoodsMetaDTO.getSeckillPrice())
                .goodsId(goodsId)
                .orderNo(orderNo)
                .requestTime(now)
                .build();

        // 执行 Redis Lua 脚本：原子校验一人一单并预扣库存
        SeckillStockDeductResultEnum deductResult = seckillStockService.preDeductStock(
                seckillOrderMqDTO, userOrderTtlSeconds,
                DateTimeUtils.toEpochMilli(activityGoodsMetaDTO.getBeginTime()),
                DateTimeUtils.toEpochMilli(activityGoodsMetaDTO.getEndTime()));


        // 判断 Lua 脚本执行结果
        // 秒杀活动还没开始
        if (Objects.equals(deductResult, SeckillStockDeductResultEnum.ACTIVITY_NOT_STARTED)) {
            throw new BizException(ResponseCodeEnum.SECKILL_ACTIVITY_NOT_STARTED);
        }

        // 秒杀活动已经结束
        if (Objects.equals(deductResult, SeckillStockDeductResultEnum.ACTIVITY_ENDED)) {
            throw new BizException(ResponseCodeEnum.SECKILL_ACTIVITY_ENDED);
        }

        // 已售罄
        if (Objects.equals(deductResult, SeckillStockDeductResultEnum.SOLD_OUT)) {
            throw new BizException(ResponseCodeEnum.SECKILL_GOODS_SOLD_OUT);
        }

        // 请勿重复参与秒杀
        if (Objects.equals(deductResult, SeckillStockDeductResultEnum.REPEATED_ORDER)) {
            throw new BizException(ResponseCodeEnum.SECKILL_ORDER_DUPLICATE);
        }


        // 记录异步处理状态，前端后续可以根据 orderNo 查询最终结果
        saveOrderStatus(userId, orderNo, OrderStatusEnum.PROCESSING.getStatus());

        // 发送 MQ，内部会携带 CorrelationData(orderNo)，方便生产者确认回调定位消息
        boolean isSendSuccess = seckillOrderMessageSender.send(seckillOrderMqDTO);

        if (!isSendSuccess) {
            log.warn("==> 秒杀下单消息发送结果未知，订单保持处理中等待后续确认, orderNo: {}", orderNo);
        }
        // 立即响参 "处理中"，扣库存 + 建订单交给消费者异步处理
        return Response.success(
                DoSeckillRspVO.builder()
                        .orderNo(orderNo)
                        .status(OrderStatusEnum.PROCESSING.getStatus())
                        .build()
        );
    }

    /**
     * 异步消费秒杀下单消息：扣减库存 + 创建订单 (消费者)
     *
     * @param message
     */
    @Override
    public void createSeckillOrder(SeckillOrderMqDTO message) {
        // 记录消费开始时间
        long startTime = System.currentTimeMillis();

        Long activityId = message.getActivityId();
        Long userId = message.getUserId();
        Long goodsId = message.getGoodsId();
        String orderNo = message.getOrderNo();

        log.info("==> 消费秒杀下单消息, orderNo: {}, userId: {}, activityId: {}, goodsId: {}",
                orderNo, userId, activityId, goodsId);
        // 6. 查询商品信息，用于冗余到订单中
//        GoodsDO goodsDO = goodsDOMapper.selectByPrimaryKey(goodsId);
        // 订单过期时间：当前时间 + 30 分钟
        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(30);

        try {
            SeckillOrderDO orderDO = transactionTemplate.execute(status -> {

                // 7. 扣减库存
                long deductStartTime = System.currentTimeMillis();

                int count = seckillGoodsDOMapper.deductStock(message.getSeckillGoodsId());

                // 扣库存耗时
                long deductCost = System.currentTimeMillis() - deductStartTime;
                log.info("#### 秒杀扣库存耗时, orderNo: {}, seckillGoodsId: {}, cost: {} ms",
                        orderNo, message.getSeckillGoodsId(), deductCost);

                if (count == 0) {
                    log.warn("==> 扣减库存失败，商品已售罄或已下架, orderNo: {}", orderNo);
                    return null;
                }

                // 8. 创建订单
                SeckillOrderDO order = SeckillOrderDO.builder()
                        .userId(userId)
                        .activityId(activityId)
                        .goodsId(goodsId)
                        .orderNo(orderNo)
                        .seckillPrice(message.getSeckillPrice())
                        .goodsName(message.getGoodsName())
                        .goodsImg(message.getGoodsImg())
                        .status(OrderStatusEnum.PENDING_PAYMENT.getStatus())
                        .expireTime(expireTime)
                        .isDeleted(0)
                        .createTime(LocalDateTime.now())
                        .updateTime(LocalDateTime.now())
                        .build();

                seckillOrderDOMapper.insert(order);
                return order;
            });

            // 扣库存失败，更新 Redis 中订单状态为秒杀失败
            if (Objects.isNull(orderDO)) {
                saveOrderStatus(userId, orderNo, OrderStatusEnum.SECKILL_FAILED.getStatus());

                // 清理 Redis 中的回补上下文
                deleteCompensationContext(orderNo);

                // 推送 sse 结果
                seckillOrderResultNotifyService.notifyOrderResult(userId,
                        buildStatusResult(orderNo, OrderStatusEnum.SECKILL_FAILED));
                return;
            }

            // 订单创建成功，更新 Redis 中订单状态为待支付
            saveOrderStatus(userId, orderNo, OrderStatusEnum.PENDING_PAYMENT.getStatus());

            // 消费者已经正常落库，删除待回补上下文，避免 Redis 中残留无效数据，占着内存
            deleteCompensationContext(orderNo);

            // 推送 sse 结果
            seckillOrderResultNotifyService.notifyOrderResult(userId, buildOrderResult(orderDO));

            log.info("==> 异步秒杀下单成功, orderNo: {}", orderNo);
        } catch (DuplicateKeyException e) {
            // 幂等兜底：order_no 唯一索引命中，说明是重复投递的消息
            // 直接当作成功处理，不再抛异常，避免消费者把消息无限重投
            log.warn("==> 重复消费秒杀消息，订单已存在，幂等返回, orderNo: {}", orderNo);

            // 不能直接写 PENDING_PAYMENT，避免把已支付、已取消等状态回退
            SeckillOrderDO existedOrderDO = seckillOrderDOMapper.selectByOrderNoAndUserId(orderNo, userId);
            if (Objects.nonNull(existedOrderDO)) {
                saveOrderStatus(userId, orderNo, existedOrderDO.getStatus());

                // 清理 Redis 中的回补上下文
                deleteCompensationContext(orderNo);

                // 推送 sse 结果
                seckillOrderResultNotifyService.notifyOrderResult(userId, buildOrderResult(existedOrderDO));
            } else {
                log.warn("==> 重复消费命中唯一索引，但未查询到当前用户订单, orderNo: {}, userId: {}", orderNo, userId);
            }
        } finally {
            log.info("#### 秒杀订单消费链路总耗时, orderNo: {}, cost: {} ms",
                    orderNo, System.currentTimeMillis() - startTime);
        }
    }

    /**
     * 查询秒杀订单处理结果
     *
     * @param reqVO
     * @return
     */
    @Override
    public Response<FindSeckillOrderResultRspVO> findSeckillOrderResult(FindSeckillOrderResultReqVO reqVO) {
        // 获取订单号
        String orderNo = reqVO.getOrderNo();

        // 获取当前登录 id
        long userId = StpUtil.getLoginIdAsLong();
        // 构建 redisKey
        String redisKey = RedisKeyConstants.SECKILL_ORDER_STATUS_PREFIX + userId + ":" + orderNo;
        // 从 Redis 中获取订单状态
        String orderStatus = stringRedisTemplate.opsForValue().get(redisKey);

        if (StrUtil.isNotBlank(orderStatus)) {
            OrderStatusEnum statusEnum = OrderStatusEnum.getByStatus(Integer.parseInt(orderStatus));

            // 如果订单状态为处理中或秒杀失败，直接返回
            if (OrderStatusEnum.PROCESSING.equals(statusEnum) || OrderStatusEnum.SECKILL_FAILED.equals(statusEnum)) {
                return Response.success(FindSeckillOrderResultRspVO.builder()
                        .orderNo(orderNo)
                        .status(statusEnum.getStatus())
                        .statusDesc(statusEnum.getDescription())
                        .build());
            }

        }
        // 如果订单状态为待支付，查询订单详情
        SeckillOrderDO orderDO = seckillOrderDOMapper.selectByOrderNoAndUserId(orderNo, userId);

        // 如果表里没订单记录，可能消费者还没来得及消费，返回处理中...
        if (Objects.isNull(orderDO)) {
            return Response.success(FindSeckillOrderResultRspVO.builder()
                    .orderNo(orderNo)
                    .status(OrderStatusEnum.PROCESSING.getStatus())
                    .statusDesc(OrderStatusEnum.PROCESSING.getDescription())
                    .build());
        }
        // 如果订单状态为待支付，返回订单详情
        return Response.success(FindSeckillOrderResultRspVO.builder()
                .orderId(orderDO.getId())
                .orderNo(orderDO.getOrderNo())
                .status(orderDO.getStatus())
                .statusDesc(OrderStatusEnum.getDescriptionByStatus(orderDO.getStatus()))
                .goodsId(orderDO.getGoodsId())
                .goodsName(orderDO.getGoodsName())
                .goodsImg(orderDO.getGoodsImg())
                .seckillPrice(orderDO.getSeckillPrice())
                .build());
    }


    /**
     * 保存秒杀订单异步处理状态
     *
     * @param userId
     * @param orderNo
     * @param status
     */
    private void saveOrderStatus(Long userId, String orderNo, Integer status) {
        String redisKey = RedisKeyConstants.SECKILL_ORDER_STATUS_PREFIX + userId + ":" + orderNo;
        stringRedisTemplate.opsForValue().set(redisKey, String.valueOf(status),
                RedisKeyConstants.SECKILL_ORDER_STATUS_TTL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * 构建秒杀订单处理结果
     *
     * @param orderDO
     * @return
     */
    private FindSeckillOrderResultRspVO buildOrderResult(SeckillOrderDO orderDO) {
        return FindSeckillOrderResultRspVO.builder()
                .orderId(orderDO.getId())
                .orderNo(orderDO.getOrderNo())
                .status(orderDO.getStatus())
                .statusDesc(OrderStatusEnum.getDescriptionByStatus(orderDO.getStatus()))
                .goodsId(orderDO.getGoodsId())
                .goodsName(orderDO.getGoodsName())
                .goodsImg(orderDO.getGoodsImg())
                .seckillPrice(orderDO.getSeckillPrice())
                .build();
    }

    /**
     * 构建秒杀订单状态结果
     *
     * @param orderNo 订单号
     * @param status  订单状态
     * @return
     */
    private FindSeckillOrderResultRspVO buildStatusResult(String orderNo, OrderStatusEnum status) {
        return FindSeckillOrderResultRspVO.builder()
                .orderNo(orderNo)
                .status(status.getStatus())
                .statusDesc(status.getDescription())
                .build();
    }

    /**
     * 删除秒杀订单待回补上下文
     *
     * @param orderNo 订单号
     */
    private void deleteCompensationContext(String orderNo) {
        String compensationKey = RedisKeyConstants.buildSeckillOrderCompensationKey(orderNo);
        stringRedisTemplate.delete(compensationKey);
    }
}
