package com.love233niang.seckill.user.service;


import com.love233niang.seckill.common.utils.Response;
import com.love233niang.seckill.user.model.vo.RegisterUserReqVO;

/**
 * @Author: hq
 * @Date: 2026/4/10 18:09
 * @Version: v1.0.0
 * @Description: 用户业务
 **/
public interface UserService {

    /**
     * 用户注册
     * @param registerUserReqVO
     * @return
     */
    Response<?> register(RegisterUserReqVO registerUserReqVO);
}

