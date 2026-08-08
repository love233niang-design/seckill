package com.love233niang.seckill.order.model.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: hq
 * @Date: 2026/7/11 18:33
 * @Version: v1.0.0
 * @Description: 查询秒杀订单结果
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindSeckillOrderResultReqVO {

    /**
     * 订单号
     */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

}

