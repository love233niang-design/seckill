package com.love233niang.seckill.goods.controller;

import com.love233niang.seckill.common.aspect.ApiOperationLog;
import com.love233niang.seckill.common.utils.Response;
import com.love233niang.seckill.goods.model.vo.PreheatActivityCacheReqVO;
import com.love233niang.seckill.goods.service.GoodsService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: hq
 * @Date: 2026/6/2 19:16
 * @Version: v1.0.0
 * @Description: 管理端 - 商品管理
 **/
@RestController
@RequestMapping("/admin/seckill/goods")
@Slf4j
public class AdminGoodsController {

    @Resource
    private GoodsService goodsService;

    @PostMapping("/cache/preheat")
    @ApiOperationLog(description = "手动预热商品缓存")
    public Response<?> preheatCache(@RequestBody @Validated PreheatActivityCacheReqVO reqVO) {
        return goodsService.preheatActivityGoods(reqVO.getActivityId());
    }

}

