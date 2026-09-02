package com.aicode.demo.domain.port;

import com.aicode.demo.domain.exception.StructuredOutputException;

/**
 * 出站端口：把模型返回的 JSON 字符串解析为领域对象。
 */
public interface StructuredOutputPort {

    /**
     * 解析模型返回内容为 JSON 对象。
     *
     * @param json 模型返回文本（可能包含 markdown 围栏）
     * @return 仅含一个 JSON 对象的 map
     * @throws StructuredOutputException 不是单 JSON 对象、或解析失败
     */
    java.util.Map<String, Object> parseObject(String json) throws StructuredOutputException;
}
