package com.aicode.framework.platform.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 执行记录 {@code output_json} 的解析/序列化（Week 23）。
 *
 * <p>审批决策需要读取原始报告并在终态回写 {@code approvalStatus/memoryId}；
 * 把 JSON 细节收在一处，避免每个用例各写一份 {@code readValue/writeValueAsString}。</p>
 */
final class PlatformRunOutputJson {

    private PlatformRunOutputJson() {
    }

    /**
     * 解析输出；空文本返回空 Map，非法 JSON 抛 {@link IllegalStateException}（数据损坏，不能静默）。
     *
     * @param objectMapper JSON 映射器
     * @param outputJson   执行记录输出
     * @return 可变 Map
     */
    static Map<String, Object> parse(ObjectMapper objectMapper, String outputJson) {
        if (outputJson == null || outputJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(
                    outputJson, new TypeReference<LinkedHashMap<String, Object>>() {
                    });
            return parsed == null ? new LinkedHashMap<>() : parsed;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to parse execution output", ex);
        }
    }

    /**
     * 序列化输出。
     *
     * @param objectMapper JSON 映射器
     * @param output       输出 Map
     * @return JSON 文本
     */
    static String toJson(ObjectMapper objectMapper, Map<String, Object> output) {
        try {
            return objectMapper.writeValueAsString(output);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize execution output", ex);
        }
    }
}
