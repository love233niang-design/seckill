package com.love233niang.seckill.order.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.Hutool;
import cn.hutool.core.util.IdUtil;
import com.love233niang.seckill.common.domain.dataobject.GoodsDO;
import com.love233niang.seckill.common.domain.dataobject.SeckillActivityDO;
import com.love233niang.seckill.common.domain.dataobject.SeckillGoodsDO;
import com.love233niang.seckill.common.domain.dataobject.SeckillOrderDO;
import com.love233niang.seckill.common.domain.mapper.*;
import com.love233niang.seckill.common.enums.ResponseCodeEnum;
import com.love233niang.seckill.common.exception.BizException;
import com.love233niang.seckill.common.utils.Response;
import com.love233niang.seckill.order.enums.OrderStatusEnum;
import com.love233niang.seckill.order.model.vo.DoSeckillReqVO;
import com.love233niang.seckill.order.model.vo.DoSeckillRspVO;
import com.love233niang.seckill.order.service.OrderService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

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

    /**
     * 秒杀下单
     *
     * @param reqVO
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<DoSeckillRspVO> doSeckill(DoSeckillReqVO reqVO) {
        Long activityId = reqVO.getActivityId();
        Long goodsId = reqVO.getGoodsId();

        // 1. 获取当前登录用户 ID
        long userId = StpUtil.getLoginIdAsLong();
        log.info("==> 当前登录用户 ID: {}", userId);

        // 2。查询活动是否存在
        SeckillActivityDO activityDO = seckillActivityDOMapper.selectByPrimaryKey(activityId);
        if (Objects.isNull(activityDO)) {
            throw new BizException(ResponseCodeEnum.SECKILL_ACTIVITY_NOT_EXIST);
        }
        // 3.活动时间校验
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activityDO.getBeginTime())) {
            throw new BizException(ResponseCodeEnum.SECKILL_ACTIVITY_NOT_STARTED);
        }
        if (now.isAfter(activityDO.getEndTime())) {
            throw new BizException(ResponseCodeEnum.SECKILL_ACTIVITY_ENDED);
        }
        // 4秒杀商品校验
        SeckillGoodsDO seckillGoodsDO = seckillGoodsDOMapper.selectByActivityIdAndGoodsId(activityId, goodsId);
        if (Objects.isNull(seckillGoodsDO)) {
            throw new BizException(ResponseCodeEnum.SECKILL_GOODS_NOT_EXIST);
        }

        // 5. 秒杀商品库存校验
        Integer seckillStock = seckillGoodsDO.getSeckillStock();
        if (seckillStock <= 0) {
            throw new BizException(ResponseCodeEnum.SECKILL_GOODS_SOLD_OUT);
        }
        // 6. 扣减库存
        int count = seckillGoodsDOMapper.deductStock(seckillGoodsDO.getId());
        if (count == 0) {
            throw new BizException(ResponseCodeEnum.SECKILL_GOODS_SOLD_OUT);
        }
        //7查询商品信息
        GoodsDO goodsDO = goodsDOMapper.selectByPrimaryKey(goodsId);
        // 创建订单
        // 使用 Hutool 提供的工具方法，通过雪花算法生成订单号
        String orderNo = IdUtil.getSnowflakeNextIdStr();
        // 订单过期时间：当前时间 + 30 分钟
        LocalDateTime expireTime = now.plusMinutes(30);
        SeckillOrderDO orderDO = SeckillOrderDO.builder()
                .userId(userId)
                .activityId(activityId)
                .goodsId(goodsId)
                .orderNo(orderNo)
                .seckillPrice(seckillGoodsDO.getSeckillPrice())
                .goodsName(goodsDO.getGoodsName())
                .goodsImg(goodsDO.getGoodsImg())
                .status(OrderStatusEnum.PENDING_PAYMENT.getStatus())
                .expireTime(expireTime)
                .isDeleted(0)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        try {
            seckillOrderDOMapper.insert(orderDO);
        } catch (DuplicateKeyException e) {
            log.warn("==> 重复下单, userId: {}, activityId: {}, goodsId: {}", userId, activityId, goodsId);
            throw new BizException(ResponseCodeEnum.SECKILL_ORDER_DUPLICATE);
        }
        log.info("==> 秒杀下单成功, orderId: {}, orderNo: {}", orderDO.getId(), orderNo);

        // 9. 组装响应数据
        DoSeckillRspVO rspVO = DoSeckillRspVO.builder()
                .orderId(orderDO.getId())
                .orderNo(orderNo)
                .goodsName(goodsDO.getGoodsName())
                .goodsImg(goodsDO.getGoodsImg())
                .seckillPrice(seckillGoodsDO.getSeckillPrice())
                .status(OrderStatusEnum.PENDING_PAYMENT.getStatus())
                .expireTime(expireTime)
                .build();
        return Response.success(rspVO);
    }
}
