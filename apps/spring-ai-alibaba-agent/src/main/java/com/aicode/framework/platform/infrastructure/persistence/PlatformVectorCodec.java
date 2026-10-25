package com.aicode.framework.platform.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;

/**
 * 记忆向量 ↔ JSON 文本编解码（Week 21，Week 23 从适配器拆出）。
 *
 * <p>空/非法值一律按空向量处理，避免一条脏数据打断整次检索。存储形态属于基础设施细节，
 * 只在 {@link JdbcPlatformMemoryAdapter} 与排序函数之间传递，不出现在对外 DTO。</p>
 */
final class PlatformVectorCodec {

    private PlatformVectorCodec() {
    }

    /**
     * 编码为 {@code [1.0,-0.5]} 形式；空向量编码为空串。
     *
     * @param vector 向量，可为 null
     * @return JSON 文本
     */
    static String encode(float[] vector) {
        if (vector == null || vector.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        return builder.append(']').toString();
    }

    /**
     * 解码；空/非法文本返回空数组。
     *
     * @param payload JSON 文本
     * @return 向量
     */
    static float[] decode(String payload) {
        if (payload == null || payload.isBlank()) {
            return new float[0];
        }
        String body = payload.trim();
        if (body.startsWith("[")) {
            body = body.substring(1);
        }
        if (body.endsWith("]")) {
            body = body.substring(0, body.length() - 1);
        }
        if (body.isBlank()) {
            return new float[0];
        }
        String[] parts = body.split(",");
        List<Float> values = new ArrayList<>(parts.length);
        for (String part : parts) {
            try {
                values.add(Float.parseFloat(part.trim()));
            } catch (NumberFormatException ex) {
                return new float[0];
            }
        }
        float[] vector = new float[values.size()];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = values.get(i);
        }
        return vector;
    }
}
