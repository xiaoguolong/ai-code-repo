package com.aicode.gateway.infrastructure.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 平台统一响应信封的 Feign 侧形态（Week 19）。
 *
 * <p>只声明网关需要的字段；{@code ignoreUnknown} 保证平台新增字段不会让网关反序列化失败
 * （前后端/前后服务独立发布时的必要防御）。</p>
 *
 * @param code    业务码
 * @param message 提示信息
 * @param data    业务数据
 * @param traceId 链路 ID
 * @param <T>     业务数据类型
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FeignEnvelope<T>(String code, String message, T data, String traceId) {

    /** 成功业务码。 */
    public static final String SUCCESS_CODE = "SUCCESS";

    /**
     * 是否成功。
     *
     * @return code 为 SUCCESS 返回 true
     */
    public boolean succeeded() {
        return SUCCESS_CODE.equalsIgnoreCase(code);
    }
}
