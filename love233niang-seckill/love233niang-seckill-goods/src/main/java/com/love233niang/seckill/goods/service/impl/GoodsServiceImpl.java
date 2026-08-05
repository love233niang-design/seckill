package com.love233niang.seckill.goods.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.love233niang.seckill.common.constant.RedisKeyConstants;
import com.love233niang.seckill.common.domain.dataobject.*;
import com.love233niang.seckill.common.domain.mapper.*;
import com.love233niang.seckill.common.enums.ActivityStatusEnum;
import com.love233niang.seckill.common.enums.ResponseCodeEnum;
import com.love233niang.seckill.common.exception.BizException;
import com.love233niang.seckill.common.utils.JsonUtils;
import com.love233niang.seckill.common.utils.Response;
import com.love233niang.seckill.goods.model.vo.FindSeckillGoodsDetailReqVO;
import com.love233niang.seckill.goods.model.vo.FindSeckillGoodsDetailRspVO;
import com.love233niang.seckill.goods.model.vo.FindSeckillGoodsListReqVO;
import com.love233niang.seckill.goods.model.vo.FindSeckillGoodsListRspVO;
import com.love233niang.seckill.goods.service.GoodsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
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
    @Autowired
    private StringRedisTemplate stringRedisTemplate;


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
        log.info("==> 查询秒杀商品列表, activityId: {}", activityId);

        String redisKey = RedisKeyConstants.GOODS_LIST_PREFIX + activityId;

        String redisJsonValue = stringRedisTemplate.opsForValue().get(redisKey);

        if (StrUtil.isNotBlank(redisJsonValue)) {
            if (Objects.equals(RedisKeyConstants.NULL_CACHE_VALUE, redisJsonValue)) {
                log.info("==> 命中空值缓存，活动不存在, redisKey: {}", redisKey);
                throw new BizException(ResponseCodeEnum.SECKILL_ACTIVITY_NOT_EXIST);
            }
            log.info("==> 命中商品列表缓存, redisKey: {}", redisKey);

            List<FindSeckillGoodsListRspVO> cachedList = JsonUtils.parseList(redisJsonValue, FindSeckillGoodsListRspVO.class);

            supplementStock(cachedList, activityId);

            FindSeckillGoodsListRspVO first = cachedList.get(0);

            ActivityStatusEnum activityStatusEnum = calculateActivityStatus(first.getBeginTime(), first.getEndTime());

            cachedList.forEach(item -> {
                item.setActivityStatus(activityStatusEnum.getStatus());
            });

            return Response.success(cachedList);
        }
        // 查询活动信息
        SeckillActivityDO activityDO = seckillActivityDOMapper.selectByPrimaryKey(activityId);
        if (Objects.isNull(activityDO)) {
            cacheNullValue(redisKey);
            throw new BizException(ResponseCodeEnum.SECKILL_ACTIVITY_NOT_EXIST);
        }
        // 根据活动id查询秒杀商品列表
        List<SeckillGoodsDO> seckillGoodsDOS = seckillGoodsDOMapper.selectByActivityId(activityId);
        if (CollectionUtils.isEmpty(seckillGoodsDOS)) {
            log.info("==> 该活动下暂无秒杀商品, activityId: {}", activityId);
            return Response.success(Collections.emptyList());
        }
        List<Long> goodsIds = seckillGoodsDOS.stream().map(SeckillGoodsDO::getGoodsId).collect(Collectors.toList());
        // 优化点：使用 selectByIds 批量查询商品信息，构建 map 集合
        // 避免了在循环中一次次查询商品信息
        List<GoodsDO> goodsDOS = goodsDOMapper.selectByIds(goodsIds);
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

        log.info("==> 商品列表缓存未命中，将数据写入 Redis, redisKey: {}", redisKey);
        Long ttlSeconds = RedisKeyConstants.calculateTtlSeconds(activityDO.getEndTime());
        if (Objects.nonNull(ttlSeconds) && ttlSeconds > 0) {
            // 活动未开始设置动态 TTL 值
            stringRedisTemplate.opsForValue().set(redisKey, JsonUtils.toJsonString(rspVOS), ttlSeconds, TimeUnit.SECONDS);
        } else {
            stringRedisTemplate.opsForValue().set(redisKey, JsonUtils.toJsonString(rspVOS), RedisKeyConstants.ENDED_ACTIVITY_TTL_MINUTES, TimeUnit.MINUTES);
        }

        return Response.success(rspVOS);
    }

    /**
     * ·
     * 实时补充库存字段（库存变化频繁，每次从数据库实时查询）
     *
     * @param goodsList  缓存中的商品列表
     * @param activityId 活动 ID
     */
    public void supplementStock(List<FindSeckillGoodsListRspVO> goodsList, Long activityId) {
        List<SeckillGoodsDO> seckillGoodsDOS = seckillGoodsDOMapper.selectStockByActivityId(activityId);

        Map<Long, Integer> stockMap = seckillGoodsDOS.stream().collect(Collectors.toMap(SeckillGoodsDO::getId, SeckillGoodsDO::getSeckillStock));

        for (FindSeckillGoodsListRspVO rspVO : goodsList) {
            Integer stock = stockMap.get(rspVO.getId());
            if (Objects.nonNull(stock)) {
                rspVO.setSeckillStock(stock);
            }
        }

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

        String redisKey = RedisKeyConstants.GOODS_DETAIL_PREFIX + activityId + ":" + goodsId;

        String redisJsonValue = stringRedisTemplate.opsForValue().get(redisKey);
        if (StrUtil.isNotBlank(redisJsonValue)) {
            if (Objects.equals(RedisKeyConstants.NULL_CACHE_VALUE, redisJsonValue)) {
                log.info("==> 命中空值缓存，商品不存在, redisKey: {}", redisKey);
                throw new BizException(ResponseCodeEnum.SECKILL_GOODS_NOT_EXIST);
            }
            log.info("==> 命中商品详情缓存, redisKey: {}", redisKey);

            FindSeckillGoodsDetailRspVO cachedDetail = JsonUtils.parseObject(redisJsonValue, FindSeckillGoodsDetailRspVO.class);

            SeckillGoodsDO seckillGoodsDO = seckillGoodsDOMapper.selectStockByActivityIdAndGoodsId(activityId, goodsId);
            if (Objects.nonNull(seckillGoodsDO)) {
                cachedDetail.setSeckillStock(seckillGoodsDO.getSeckillStock());
            }


            ActivityStatusEnum activityStatusEnum = calculateActivityStatus(cachedDetail.getBeginTime(), cachedDetail.getEndTime());
            cachedDetail.setActivityStatus(activityStatusEnum.getStatus());
            return Response.success(cachedDetail);
        }

        // 1. 根据活动 ID 查询活动信息，校验活动是否存在
        SeckillActivityDO activityDO = seckillActivityDOMapper.selectByPrimaryKey(activityId);
        if (Objects.isNull(activityDO)) {
            cacheNullValue(redisKey);
            throw new BizException(ResponseCodeEnum.SECKILL_ACTIVITY_NOT_EXIST);
        }

        // 2. 根据活动 ID 和商品 ID 查询秒杀商品
        SeckillGoodsDO seckillGoodsDO = seckillGoodsDOMapper.selectByActivityIdAndGoodsId(activityId, goodsId);
        if (Objects.isNull(seckillGoodsDO)) {
            cacheNullValue(redisKey);
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

        log.info("==> 商品详情缓存未命中，将数据写入 Redis, redisKey: {}", redisKey);
        Long ttlSeconds = RedisKeyConstants.calculateTtlSeconds(activityDO.getEndTime());
        if (Objects.nonNull(ttlSeconds) && ttlSeconds > 0) {
            stringRedisTemplate.opsForValue().set(redisKey, JsonUtils.toJsonString(rspVO), ttlSeconds, TimeUnit.SECONDS);
        } else {
            stringRedisTemplate.opsForValue().set(redisKey, JsonUtils.toJsonString(rspVO), RedisKeyConstants.ENDED_ACTIVITY_TTL_MINUTES, TimeUnit.MINUTES);

        }

        return Response.success(rspVO);
    }


    /**
     * 预热指定活动的商品缓存
     *
     * @param activityId
     * @return
     */
    @Override
    public Response<?> preheatActivityGoods(Long activityId) {
        log.info("==> 开始预热活动商品缓存, activityId: {}", activityId);
        // 1. 查询活动信息（获取活动结束时间，用于计算动态 TTL）
        SeckillActivityDO activityDO = seckillActivityDOMapper.selectByPrimaryKey(activityId);
        if (Objects.isNull(activityDO)) {
            throw new BizException(ResponseCodeEnum.SECKILL_ACTIVITY_NOT_EXIST);
        }
        // 2. 计算动态缓存 TTL 过期时间
        Long ttlSeconds = RedisKeyConstants.calculateTtlSeconds(activityDO.getEndTime());
        if (Objects.isNull(ttlSeconds) || ttlSeconds <= 0) {
            log.info("==> 预热跳过：活动已结束, activityId: {}", activityId);
            throw new BizException(ResponseCodeEnum.SECKILL_ACTIVITY_ENDED);
        }
        // 3. 查询该活动下所有秒杀商品
        List<SeckillGoodsDO> seckillGoodsDOS = seckillGoodsDOMapper.selectByActivityId(activityId);
        if (CollUtil.isEmpty(seckillGoodsDOS)) {
            log.info("==> 预热跳过：活动下无商品, activityId: {}", activityId);
            throw new BizException(ResponseCodeEnum.SECKILL_ACTIVITY_GOODS_EMPTY);
        }
        // 4. 批量查询商品原价
        List<Long> goodsIds = seckillGoodsDOS.stream().map(SeckillGoodsDO::getGoodsId).collect(Collectors.toList());
        List<GoodsDO> goodsDOS = goodsDOMapper.selectByIds(goodsIds);
        Map<Long, GoodsDO> goodsMap = goodsDOS.stream()
                .collect(Collectors.toMap(GoodsDO::getId, goodsDO -> goodsDO));

        // 5. 预热商品列表缓存
        String listKey = RedisKeyConstants.GOODS_LIST_PREFIX + activityId;
        List<FindSeckillGoodsListRspVO> listRspVOS = new ArrayList<>();
        for (SeckillGoodsDO sg : seckillGoodsDOS) {
            FindSeckillGoodsListRspVO vo = new FindSeckillGoodsListRspVO();
            vo.setId(sg.getId());
            vo.setGoodsId(sg.getGoodsId());
            vo.setActivityId(sg.getActivityId());
            vo.setSeckillTitle(sg.getSeckillTitle());
            vo.setSeckillImg(sg.getSeckillImg());
            vo.setSeckillPrice(sg.getSeckillPrice());
            vo.setSeckillTotal(sg.getSeckillTotal());
            vo.setSeckillStock(sg.getSeckillStock());
            vo.setActivityStatus(calculateActivityStatus(activityDO).getStatus());
            vo.setBeginTime(activityDO.getBeginTime());
            vo.setEndTime(activityDO.getEndTime());

            GoodsDO goodsDO = goodsMap.get(sg.getGoodsId());
            if (Objects.nonNull(goodsDO)) {
                vo.setGoodsPrice(goodsDO.getGoodsPrice());
            }

            listRspVOS.add(vo);
        }

        stringRedisTemplate.opsForValue().set(listKey, JsonUtils.toJsonString(listRspVOS), ttlSeconds, TimeUnit.SECONDS);
        log.info("==> 预热商品列表缓存成功, key: {}, TTL: {}s", listKey, ttlSeconds);

        // 6. 预热每个商品的详情缓存
        for (SeckillGoodsDO sg : seckillGoodsDOS) {
            String detailKey = RedisKeyConstants.GOODS_DETAIL_PREFIX + activityId + ":" + sg.getGoodsId();
            // 查询商品基本信息
            GoodsDO goodsDO = goodsMap.get(sg.getGoodsId());

            // 查询商品轮播图
            List<GoodsImgDO> goodsImgDOS = goodsImgDOMapper.selectByGoodsId(sg.getGoodsId());
            List<String> goodsImgs = null;
            if (CollUtil.isNotEmpty(goodsImgDOS)) {
                goodsImgs = goodsImgDOS.stream()
                        .map(GoodsImgDO::getImgUrl)
                        .toList();
            }

            // 查询商品详情 HTML
            GoodsDetailDO goodsDetailDO = goodsDetailDOMapper.selectByGoodsId(sg.getGoodsId());

            // 组装详情 VO
            FindSeckillGoodsDetailRspVO detailVO = FindSeckillGoodsDetailRspVO.builder()
                    .id(sg.getId())
                    .goodsId(sg.getGoodsId())
                    .activityId(sg.getActivityId())
                    .seckillPrice(sg.getSeckillPrice())
                    .seckillTotal(sg.getSeckillTotal())
                    .seckillStock(sg.getSeckillStock())
                    .activityStatus(calculateActivityStatus(activityDO).getStatus())
                    .beginTime(activityDO.getBeginTime())
                    .endTime(activityDO.getEndTime())
                    .goodsImgs(goodsImgs)
                    .build();

            // 设置商品名称和原价
            if (Objects.nonNull(goodsDO)) {
                detailVO.setGoodsName(goodsDO.getGoodsName());
                detailVO.setGoodsPrice(goodsDO.getGoodsPrice());
            }

            // 设置商品详情 HTML
            if (Objects.nonNull(goodsDetailDO)) {
                detailVO.setGoodsDetail(goodsDetailDO.getDetailContent());
            }

            stringRedisTemplate.opsForValue().set(detailKey, JsonUtils.toJsonString(detailVO),
                    ttlSeconds, TimeUnit.SECONDS);
        }
        log.info("==> 预热活动 {} 的 {} 个商品详情缓存完成", activityId, seckillGoodsDOS.size());

        return Response.success();
    }


    /**
     * 根据当前时间动态计算活动状态
     *
     * @param activityDO
     * @return
     */
    private ActivityStatusEnum calculateActivityStatus(SeckillActivityDO activityDO) {
        return calculateActivityStatus(activityDO.getBeginTime(), activityDO.getEndTime());
    }

    /**
     * 根据当前时间动态计算活动状态 (重载方法)
     *
     * @param beginTime
     * @param endTime
     * @return
     */
    private ActivityStatusEnum calculateActivityStatus(LocalDateTime beginTime, LocalDateTime endTime) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(beginTime)) { // 当前时间早于活动开始时间，则活动未开始
            return ActivityStatusEnum.NOT_STARTED;
        } else if (now.isAfter(endTime)) { // 当前时间晚于活动结束时间，则活动已结束
            return ActivityStatusEnum.ENDED;
        } else { // 活动进行中
            return ActivityStatusEnum.ING;
        }
    }

    /**
     * 缓存空值，防止缓存穿透
     *
     * @param redisKey
     */
    private void cacheNullValue(String redisKey) {
        stringRedisTemplate.opsForValue().set(redisKey, RedisKeyConstants.NULL_CACHE_VALUE,
                RedisKeyConstants.NULL_CACHE_TTL_MINUTES, TimeUnit.MINUTES);

        log.info("==> 缓存空值，防止穿透, redisKey: {}, TTL: {}min", redisKey, RedisKeyConstants.NULL_CACHE_TTL_MINUTES);
    }

}
