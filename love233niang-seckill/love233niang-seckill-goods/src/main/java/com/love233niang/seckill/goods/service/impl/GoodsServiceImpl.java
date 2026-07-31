package com.love233niang.seckill.goods.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.love233niang.seckill.common.domain.dataobject.*;
import com.love233niang.seckill.common.domain.mapper.*;
import com.love233niang.seckill.common.enums.ActivityStatusEnum;
import com.love233niang.seckill.common.enums.ResponseCodeEnum;
import com.love233niang.seckill.common.exception.BizException;
import com.love233niang.seckill.common.utils.Response;
import com.love233niang.seckill.goods.model.vo.FindSeckillGoodsDetailReqVO;
import com.love233niang.seckill.goods.model.vo.FindSeckillGoodsDetailRspVO;
import com.love233niang.seckill.goods.model.vo.FindSeckillGoodsListReqVO;
import com.love233niang.seckill.goods.model.vo.FindSeckillGoodsListRspVO;
import com.love233niang.seckill.goods.service.GoodsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GoodsServiceImpl implements GoodsService {
    @Autowired
    private SeckillGoodsDOMapper seckillGoodsDOMapper;
    @Autowired
    private SeckillActivityDOMapper seckillActivityDOMapper;
    @Autowired
    private GoodsDOMapper goodsDOMapper;
    @Autowired
    private GoodsImgDOMapper goodsImgDOMapper;
    @Autowired
    private GoodsDetailDOMapper goodsDetailDOMapper;


    /**
     * 查询秒杀商品列表
     *
     * @param reqVO
     * @return
     */
    @Override
    public Response<List<FindSeckillGoodsListRspVO>> findSeckillGoodsList(FindSeckillGoodsListReqVO reqVO) {
        // 获取活动id
        Long activityId = reqVO.getActivityId();
        // 查询活动信息
        SeckillActivityDO activityDO = seckillActivityDOMapper.selectByPrimaryKey(activityId);
        if (Objects.isNull(activityDO)) {
            throw new BizException(ResponseCodeEnum.SECKILL_ACTIVITY_NOT_EXIST);
        }
        // 根据活动id查询秒杀商品列表
        List<SeckillGoodsDO> seckillGoodsDOS = seckillGoodsDOMapper.selectByActivityId(activityId);
        if (CollectionUtils.isEmpty(seckillGoodsDOS)) {
            log.info("==> 该活动下暂无秒杀商品, activityId: {}", activityId);
            return Response.success(Collections.emptyList());
        }
        List<Long> goodsId = seckillGoodsDOS.stream().map(SeckillGoodsDO::getGoodsId).collect(Collectors.toList());
        List<GoodsDO> goodsDOS = goodsDOMapper.selectByIds(goodsId);

        Map<Long, GoodsDO> goodsMap = goodsDOS.stream().collect(Collectors.toMap(GoodsDO::getId, goodsDO -> goodsDO));

        // 4. 计算活动状态（基于当前时间动态判断）
        ActivityStatusEnum activityStatusEnum = calculateActivityStatus(activityDO);
        List<FindSeckillGoodsListRspVO> rspVOS = new ArrayList<>();
        for (SeckillGoodsDO seckillGoodsDO : seckillGoodsDOS) {
            FindSeckillGoodsListRspVO rspVO = new FindSeckillGoodsListRspVO();
            rspVO.setId(seckillGoodsDO.getId());
            rspVO.setGoodsId(seckillGoodsDO.getGoodsId());
            rspVO.setActivityId(seckillGoodsDO.getActivityId());
            rspVO.setSeckillTitle(seckillGoodsDO.getSeckillTitle());
            rspVO.setSeckillImg(seckillGoodsDO.getSeckillImg());
            rspVO.setSeckillPrice(seckillGoodsDO.getSeckillPrice());
            rspVO.setSeckillTotal(seckillGoodsDO.getSeckillTotal());
            rspVO.setSeckillStock(seckillGoodsDO.getSeckillStock());
            rspVO.setActivityStatus(activityStatusEnum.getStatus());
            rspVO.setBeginTime(activityDO.getBeginTime());
            rspVO.setEndTime(activityDO.getEndTime());

            // 设置商品原价
            GoodsDO goodsDO = goodsMap.get(seckillGoodsDO.getGoodsId());
            if (Objects.nonNull(goodsDO)) {
                rspVO.setGoodsPrice(goodsDO.getGoodsPrice());
            }

            rspVOS.add(rspVO);
        }
        return Response.success(rspVOS);
    }

    /**
     * 查询秒杀商品详情
     *
     * @param reqVO
     * @return
     */
    @Override
    public Response<FindSeckillGoodsDetailRspVO> findSeckillGoodsDetail(FindSeckillGoodsDetailReqVO reqVO) {
        // 商品 ID
        Long goodsId = reqVO.getGoodsId();
        // 活动 ID
        Long activityId = reqVO.getActivityId();
        log.info("==> 查询秒杀商品详情, goodsId: {}, activityId: {}", goodsId, activityId);

        // 1. 根据活动 ID 查询活动信息，校验活动是否存在
        SeckillActivityDO activityDO = seckillActivityDOMapper.selectByPrimaryKey(activityId);
        if (Objects.isNull(activityDO)) {
            throw new BizException(ResponseCodeEnum.SECKILL_ACTIVITY_NOT_EXIST);
        }

        // 2. 根据活动 ID 和商品 ID 查询秒杀商品
        SeckillGoodsDO seckillGoodsDO = seckillGoodsDOMapper.selectByActivityIdAndGoodsId(activityId, goodsId);
        if (Objects.isNull(seckillGoodsDO)) {
            throw new BizException(ResponseCodeEnum.SECKILL_GOODS_NOT_EXIST);
        }

        // 3. 根据 goodsId 查询商品基本信息, 如商品名称、原价
        GoodsDO goodsDO = goodsDOMapper.selectByPrimaryKey(goodsId);

        // 4. 根据 goodsId 查询商品轮播图列表
        List<GoodsImgDO> goodsImgDOS = goodsImgDOMapper.selectByGoodsId(goodsId);

        List<String> goodsImgs = null;
        if (CollUtil.isNotEmpty(goodsImgDOS)) {
            goodsImgs = goodsImgDOS.stream()
                    .map(GoodsImgDO::getImgUrl)
                    .toList();
        }

        // 5. 根据 goodsId 查询商品详情 HTML
        GoodsDetailDO goodsDetailDO = goodsDetailDOMapper.selectByGoodsId(goodsId);

        // 6. 计算活动状态
        ActivityStatusEnum activityStatusEnum = calculateActivityStatus(activityDO);

        // 7. 组装响应数据
        FindSeckillGoodsDetailRspVO rspVO = FindSeckillGoodsDetailRspVO.builder()
                .id(seckillGoodsDO.getId())
                .goodsId(goodsDO.getId())
                .activityId(seckillGoodsDO.getActivityId())
                .seckillPrice(seckillGoodsDO.getSeckillPrice())
                .seckillTotal(seckillGoodsDO.getSeckillTotal())
                .seckillStock(seckillGoodsDO.getSeckillStock())
                .activityStatus(activityStatusEnum.getStatus())
                .beginTime(activityDO.getBeginTime())
                .endTime(activityDO.getEndTime())
                .goodsImgs(goodsImgs)
                .build();

        // 设置商品基本信息
        if (Objects.nonNull(goodsDO)) {
            rspVO.setGoodsName(goodsDO.getGoodsName());
            rspVO.setGoodsPrice(goodsDO.getGoodsPrice());
        }

        // 设置商品详情 HTML
        if (Objects.nonNull(goodsDetailDO)) {
            rspVO.setGoodsDetail(goodsDetailDO.getDetailContent());
        }

        return Response.success(rspVO);
    }


    /**
     * 计算活动状态（基于当前时间动态判断）
     *
     * @param activityDO
     * @return
     */
    private ActivityStatusEnum calculateActivityStatus(SeckillActivityDO activityDO) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activityDO.getBeginTime())) { // 当前时间早于活动开始时间，则活动未开始
            return ActivityStatusEnum.NOT_STARTED;
        } else if (now.isAfter(activityDO.getEndTime())) { // 当前时间晚于活动结束时间，则活动已结束
            return ActivityStatusEnum.ENDED;
        } else { // 活动进行中
            return ActivityStatusEnum.ING;
        }
    }
}
