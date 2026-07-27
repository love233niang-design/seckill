package com.love233niang.seckill.user.service;


import com.love233niang.seckill.common.utils.Response;
import com.love233niang.seckill.user.model.vo.LoginUserReqVO;
import com.love233niang.seckill.user.model.vo.LoginUserRspVO;
import com.love233niang.seckill.user.model.vo.RegisterUserReqVO;
import com.love233niang.seckill.user.model.vo.SendVerifyCodeReqVO;

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

    /**
     * 用户登录
     * @param loginUserReqVO
     * @return
     */
    Response<LoginUserRspVO> login(LoginUserReqVO loginUserReqVO);

    /**
     * 发送验证码
     * @param sendVerifyCodeReqVO
     * @return
     */
    Response<?> sendVerifyCode(SendVerifyCodeReqVO sendVerifyCodeReqVO);
}

