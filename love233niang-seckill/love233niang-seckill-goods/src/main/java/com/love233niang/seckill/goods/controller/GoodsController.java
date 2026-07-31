package com.love233niang.seckill.goods.controller;

import com.love233niang.seckill.common.aspect.ApiOperationLog;
import com.love233niang.seckill.common.utils.Response;
import com.love233niang.seckill.goods.model.vo.FindSeckillGoodsDetailReqVO;
import com.love233niang.seckill.goods.model.vo.FindSeckillGoodsDetailRspVO;
import com.love233niang.seckill.goods.model.vo.FindSeckillGoodsListReqVO;
import com.love233niang.seckill.goods.model.vo.FindSeckillGoodsListRspVO;
import com.love233niang.seckill.goods.service.GoodsService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Author: 犬小哈
 * @Date: 2026/4/30 20:39
 * @Version: v1.0.0
 * @Description: 商品模块
 **/
@RestController
@RequestMapping("/seckill/goods")
@Slf4j
public class GoodsController {

    @Resource
    private GoodsService goodsService;

    /**
     * 查询秒杀商品列表
     *
     * @param reqVO
     * @return
     */
    @PostMapping("/list")
    @ApiOperationLog(description = "查询秒杀商品列表")
    public Response<List<FindSeckillGoodsListRspVO>> getSeckillGoodsList(@RequestBody @Validated FindSeckillGoodsListReqVO reqVO) {
        return goodsService.findSeckillGoodsList(reqVO);
    }

    /**
     * 查询秒杀商品详情
     *
     * @param reqVO
     * @return
     */
    @PostMapping("/detail")
    @ApiOperationLog(description = "查询秒杀商品详情")
    public Response<FindSeckillGoodsDetailRspVO> getSeckillGoodsDetail(@RequestBody @Validated FindSeckillGoodsDetailReqVO reqVO) {
        return goodsService.findSeckillGoodsDetail(reqVO);
    }
}

