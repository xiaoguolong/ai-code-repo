package com.aicode.demo.infrastructure.ocr;

import com.aicode.demo.domain.exception.OcrException;
import com.aicode.demo.domain.model.OcrFileType;
import com.aicode.demo.domain.port.DocumentOcrPort;
import com.aicode.demo.infrastructure.config.OcrProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 千帆 PaddleOCR-VL 适配器。换供应商只改 ocr.provider 与 base-url，不改端口。
 */
@Component
@ConditionalOnProperty(name = "ocr.provider", havingValue = "qianfan", matchIfMissing = true)
public class PaddleOcrVlAdapter implements DocumentOcrPort {

    private final RestClient restClient;
    private final PaddleOcrResponseMapper mapper;
    private final OcrProperties properties;

    public PaddleOcrVlAdapter(
            RestClient ocrRestClient,
            PaddleOcrResponseMapper mapper,
            OcrProperties properties
    ) {
        this.restClient = ocrRestClient;
        this.mapper = mapper;
        this.properties = properties;
    }

    /**
     * 调用 {baseUrl}/v2/ocr/paddleocr。密钥只放请求头，禁止写入日志。
     */
    @Override
    public String recognize(String file, OcrFileType fileType) {
        String apiKey = properties.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new OcrException("OCR_API_KEY is not configured");
        }
        try {
            String body = restClient.post()
                    .uri("/v2/ocr/paddleocr")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildPayload(properties.model(), file, fileType))
                    .retrieve()
                    .onStatus(status -> status.isError(), (request, response) -> {
                        throw new OcrException("OCR HTTP " + response.getStatusCode().value());
                    })
                    .body(String.class);
            return mapper.map(body);
        } catch (OcrException ex) {
            throw ex;
        } catch (Exception ex) {
            // 保留根因信息，便于服务端日志定位；不把根因直接暴露给客户端。
            throw new OcrException("OCR call failed: " + rootMessage(ex), ex);
        }
    }

    /**
     * 组装请求体。fileType 仅在非空时下发（URL 由服务端推断类型）。
     */
    static Map<String, Object> buildPayload(String model, String file, OcrFileType fileType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("file", file);
        if (fileType != null) {
            payload.put("fileType", fileType.apiValue());
        }
        return payload;
    }

    private String rootMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
