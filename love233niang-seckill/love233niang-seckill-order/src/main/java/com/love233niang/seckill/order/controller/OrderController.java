package com.love233niang.seckill.order.controller;

import com.love233niang.seckill.common.aspect.ApiOperationLog;
import com.love233niang.seckill.common.utils.Response;
import com.love233niang.seckill.order.model.vo.DoSeckillReqVO;
import com.love233niang.seckill.order.model.vo.DoSeckillRspVO;
import com.love233niang.seckill.order.model.vo.FindSeckillOrderResultReqVO;
import com.love233niang.seckill.order.model.vo.FindSeckillOrderResultRspVO;
import com.love233niang.seckill.order.service.OrderService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @Resource
    private OrderService orderService;

    /**
     * 秒杀下单
     *
     * @param reqVO
     * @return
     */
    @PostMapping
    @ApiOperationLog(description = "秒杀下单")
    public Response<DoSeckillRspVO> doSeckill(@RequestBody @Validated DoSeckillReqVO reqVO) {
        return orderService.doSeckill(reqVO);
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

}
