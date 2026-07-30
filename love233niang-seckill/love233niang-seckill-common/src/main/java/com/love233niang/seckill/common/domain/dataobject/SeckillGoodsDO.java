package com.love233niang.seckill.common.domain.dataobject;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SeckillGoodsDO {
    private Long id;

    private Long activityId;

    private Long goodsId;

    private String seckillTitle;

    private String seckillImg;

    private BigDecimal seckillPrice;

    private Integer seckillTotal;

    private Integer seckillStock;

    private Integer seckillLimit;

    private Integer sort;

    private Integer version;

    private Integer isDeleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}