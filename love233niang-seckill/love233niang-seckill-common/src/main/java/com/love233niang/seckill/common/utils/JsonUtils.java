package com.love233niang.seckill.common.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.SneakyThrows;

import java.util.List;

/**
 * @author: hq
 * @url: www.love233niang.com
 * @date: 2023-08-14 16:27
 * @description: JSON 工具类
 **/
public class JsonUtils {

    private static ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        // 反序列化时，如果 JSON 中有 Java 对象里不存在的字段，默认会报错。设置为 false 后会自动忽略这些未知字段。这在对接第三方接口时非常有用，对方多传了几个字段也不影响我们解析；
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 序列化时，如果对象没有任何属性（比如空对象），默认会报错。设置为 false 后会序列化为空 JSON {}，避免异常；
        OBJECT_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        // 注册 Java 8 日期时间模块，让 Jackson 能正确处理 LocalDateTime 等类型，序列化后是可读的日期格式，而不是一堆时间戳数字；
        OBJECT_MAPPER.registerModules(new JavaTimeModule()); // 解决 LocalDateTime 的序列化问题
    }

    /**
     * 将对象转换为 JSON 字符串
     *
     * @param obj
     * @return
     */
    @SneakyThrows
    // Lombok 注解，自动把 checked exception 包装成 unchecked exception 抛出，
    // 让方法签名更干净，不需要手动抛出 throws JsonProcessingException 异常。
    public static String toJsonString(Object obj) {
        return OBJECT_MAPPER.writeValueAsString(obj);
    }

    /**
     * 将 JSON 字符串转换为指定类型的集合
     *
     * @param json
     * @param clazz
     * @return
     * @param <T>
     */
    @SneakyThrows
    public static <T> List<T> parseList(String json, Class<T> clazz) {
        return OBJECT_MAPPER.readValue(json,
                OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, clazz));
    }

    /**
     * 初始化 ObjectMapper，供 JacksonConfig 调用，统一序列化行为
     *
     * @param objectMapper
     */
    public static void init(ObjectMapper objectMapper) {
        OBJECT_MAPPER = objectMapper;
    }
}

