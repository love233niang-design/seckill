package com.love233niang.seckill.user.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.love233niang.seckill.common.domain.dataobject.UserDO;
import com.love233niang.seckill.common.domain.mapper.UserDOMapper;
import com.love233niang.seckill.common.enums.ResponseCodeEnum;
import com.love233niang.seckill.common.exception.BizException;
import com.love233niang.seckill.common.utils.Response;
import com.love233niang.seckill.user.enums.UserStatusEnum;
import com.love233niang.seckill.user.model.vo.RegisterUserReqVO;
import com.love233niang.seckill.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@Slf4j
public class UserServiceImpl implements UserService {
    @Autowired
    private UserDOMapper userDOMapper;

    @Override
    public Response<?> register(RegisterUserReqVO registerUserReqVO) {
        String mobile = registerUserReqVO.getMobile();
        String password = registerUserReqVO.getPassword();
        String verifyCode = registerUserReqVO.getVerifyCode();

        // 校验验证码
        // todo 先写死验证码，后续开发发送接口
        if (!"123456".equals(verifyCode)) {
            throw new BizException(ResponseCodeEnum.USER_VERIFY_CODE_ERROR);
        }

        // 校验手机号是否已经注册
        Long existUserId = userDOMapper.selectIdByMobile(mobile);
        if (Objects.nonNull(existUserId)) {
            throw new BizException(ResponseCodeEnum.USER_MOBILE_EXISTS);
        }

        // 密码加密 ByCrypt 算法
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        String encodedPassword = passwordEncoder.encode(password);

        // 构建用户实体，插入数据库
        UserDO userDO = UserDO.builder()
                .mobile(mobile)
                .password(encodedPassword)
                .nickname(generateNickname())
                .status(UserStatusEnum.ENABLED.getCode())
                .build();

        userDOMapper.insertSelective(userDO);
        log.info("==> 用户注册成功, mobile: {}", mobile);

        return Response.success();
    }

    /**
     * 生成随机昵称
     * 格式：用户 + 6 位随机数字，如：用户382910
     *
     * @return 昵称
     */
    private String generateNickname() {
        return "用户" + RandomUtil.randomNumbers(6);
    }
}
