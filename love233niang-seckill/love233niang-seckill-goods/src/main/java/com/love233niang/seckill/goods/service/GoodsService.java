package com.love233niang.seckill.goods.service;

import com.love233niang.seckill.common.utils.Response;
import com.love233niang.seckill.goods.model.vo.FindSeckillGoodsDetailReqVO;
import com.love233niang.seckill.goods.model.vo.FindSeckillGoodsDetailRspVO;
import com.love233niang.seckill.goods.model.vo.FindSeckillGoodsListReqVO;
import com.love233niang.seckill.goods.model.vo.FindSeckillGoodsListRspVO;

import java.util.List;

/**
 * @Author: hq
 * @Date: 2026/4/30 20:11
 * @Version: v1.0.0
 * @Description: 商品模块业务
 **/
public interface GoodsService {

    /**
     * 查询秒杀商品列表
     *
     * @param reqVO
     * @return
     */
    Response<List<FindSeckillGoodsListRspVO>> findSeckillGoodsList(FindSeckillGoodsListReqVO reqVO);

    /**
     * 查询秒杀商品详情
     *
     * @param reqVO
     * @return
     */
    Response<FindSeckillGoodsDetailRspVO> findSeckillGoodsDetail(FindSeckillGoodsDetailReqVO reqVO);

    /**
     * 预热指定活动的商品缓存
     *
     * @param activityId
     * @return
     */
    Response<?> preheatActivityGoods(Long activityId);
}

