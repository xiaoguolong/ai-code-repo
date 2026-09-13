package com.aicode.patient.domain.model;

/**
 * 单轮模型输出的解析结果。
 *
 * @param finished    是否已产出最终答案
 * @param thought     思考内容，可为空串
 * @param action      行动内容，可为空串
 * @param observation 观察内容，可为空串
 * @param answer      最终答案；未完成时为 null
 */
public record ReActTurn(
        boolean finished,
        String thought,
        String action,
        String observation,
        String answer
) {
}
