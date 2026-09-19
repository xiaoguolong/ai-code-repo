package com.aicode.core.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OCR 接入配置。apiKey 只允许来自环境变量，禁止写入仓库。
 *
 * @param provider        qianfan，默认 qianfan（可插拔，后续换厂商只改此项）
 * @param baseUrl         千帆 OCR 端点，默认 https://qianfan.baidubce.com
 * @param apiKey          对应 OCR_API_KEY
 * @param model           默认 paddleocr-vl-0.9b
 * @param timeoutSeconds  读超时秒数，默认 120（OCR 大文件 base64 上传较慢）
 */
@ConfigurationProperties(prefix = "ocr")
public record OcrProperties(
        String provider,
        String baseUrl,
        String apiKey,
        String model,
        int timeoutSeconds
) {

    /**
     * @return 有效超时秒数，缺省或非法时回退 120
     */
    public int resolvedTimeoutSeconds() {
        return timeoutSeconds <= 0 ? 120 : timeoutSeconds;
    }
}
