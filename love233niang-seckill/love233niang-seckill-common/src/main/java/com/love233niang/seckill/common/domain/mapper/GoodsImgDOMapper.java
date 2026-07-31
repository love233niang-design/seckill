package com.love233niang.seckill.common.domain.mapper;

import com.love233niang.seckill.common.domain.dataobject.GoodsImgDO;

import java.util.List;

public interface GoodsImgDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(GoodsImgDO record);

    int insertSelective(GoodsImgDO record);

    GoodsImgDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(GoodsImgDO record);

    int updateByPrimaryKey(GoodsImgDO record);

    /**
     * 根据商品 ID 查询轮播图列表（按 sort 升序排列）
     */
    List<GoodsImgDO> selectByGoodsId(Long goodsId);
}