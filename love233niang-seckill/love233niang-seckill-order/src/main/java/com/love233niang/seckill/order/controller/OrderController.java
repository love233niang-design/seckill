package com.love233niang.seckill.order.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.love233niang.seckill.common.aspect.ApiOperationLog;
import com.love233niang.seckill.common.utils.IpUtils;
import com.love233niang.seckill.common.utils.Response;
import com.love233niang.seckill.order.model.vo.*;
import com.love233niang.seckill.order.service.OrderService;
import com.love233niang.seckill.order.service.SeckillOrderResultNotifyService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * @Author: hq
 * @Date: 2026/5/8 21:40
 * @Version: v1.0.0
 * @Description: 订单模块
 **/
@RestController
@RequestMapping("/seckill/order")
@Slf4j
public class OrderController {

    @Autowired
    private OrderService orderService;
    @Autowired
    private SeckillOrderResultNotifyService seckillOrderResultNotifyService;

    /**
     * 秒杀下单
     *
     * @param reqVO
     * @return
     */
    @PostMapping
//    @ApiOperationLog(description = "秒杀下单")
    public Response<DoSeckillRspVO> doSeckill(@RequestBody @Validated DoSeckillReqVO reqVO,
                                              HttpServletRequest request) {
        // 获取用户 IP
        String clientIp = IpUtils.getClientIp(request);
        return orderService.doSeckill(reqVO, clientIp);
    }

    /**
     * 查询秒杀订单处理结果
     *
     * @param reqVO
     * @return
     */
    @PostMapping("/result")
    @ApiOperationLog(description = "查询秒杀订单处理结果")
    public Response<FindSeckillOrderResultRspVO> findSeckillOrderResult(@RequestBody @Validated FindSeckillOrderResultReqVO reqVO) {
        return orderService.findSeckillOrderResult(reqVO);
    }

    /**
     * 订阅秒杀订单处理结果
     *
     * @param reqVO
     * @return
     */
    @PostMapping(value = "/result/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiOperationLog(description = "订阅秒杀订单处理结果")
    public SseEmitter subscribeSeckillOrderResult(@RequestBody @Validated SubscribeSeckillOrderResultReqVO reqVO) {
        // 获取当前登录用户 ID
        long userId = StpUtil.getLoginIdAsLong();
        return seckillOrderResultNotifyService.subscribe(userId, reqVO.getOrderNo());
    }
}
