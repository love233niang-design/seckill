package com.love233niang.seckill.user.controller;

import com.love233niang.seckill.common.aspect.ApiOperationLog;
import com.love233niang.seckill.common.utils.Response;
import com.love233niang.seckill.user.model.vo.RegisterUserReqVO;
import com.love233niang.seckill.user.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: hq
 * @Date: 2026/4/10 18:49
 * @Version: v1.0.0
 * @Description: 用户接口
 **/
@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    @ApiOperationLog(description = "用户注册")
    public Response<?> register(@Validated @RequestBody RegisterUserReqVO registerUserReqVO) {
        return userService.register(registerUserReqVO);
    }

}

