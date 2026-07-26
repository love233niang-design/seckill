package com.love233niang.seckill.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * @Author: 犬小哈
 * @Date: 2026/3/31 21:40
 * @Version: v1.0.0
 * @Description: 秒杀系统启动类
 **/
@SpringBootApplication
@MapperScan("com.love233niang.seckill.common.domain.mapper")
@ComponentScan({"com.love233niang.seckill.*"}) // 多模块项目中，必需手动指定扫描 com.love233niang.seckill 包下面的所有类
public class SeckillApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeckillApplication.class, args);
    }
}

