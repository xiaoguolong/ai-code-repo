package com.aicode.core.infrastructure.ocr;

import com.aicode.core.domain.exception.OcrException;
import com.aicode.core.domain.model.OcrFileType;
import com.aicode.core.domain.model.OcrResult;
import com.aicode.core.domain.port.DocumentOcrPort;
import com.aicode.core.infrastructure.config.OcrProperties;
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
    public OcrResult recognize(String file, OcrFileType fileType) {
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
            throw new OcrException("OCR call failed: " + rootMessage(ex), ex);
        }
    }

    /** 千帆 PaddleOCR 特有：返回版面检测/阅读顺序/预处理可视化图。 */
    private static final boolean VISUALIZE = true;

    /** 千帆 PaddleOCR 特有：启用版面检测。 */
    private static final boolean USE_LAYOUT_DETECTION = true;

    /** 千帆 PaddleOCR 特有：启用图表识别。 */
    private static final boolean USE_CHART_RECOGNITION = true;

    /**
     * 组装请求体。fileType 仅在非空时下发（URL 由服务端推断类型）；
     * visualize / useLayoutDetection / useChartRecognition 为千帆适配器特有参数，固定下发。
     */
    static Map<String, Object> buildPayload(String model, String file, OcrFileType fileType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("file", file);
        if (fileType != null) {
            payload.put("fileType", fileType.apiValue());
        }
        payload.put("visualize", VISUALIZE);
        payload.put("useLayoutDetection", USE_LAYOUT_DETECTION);
        payload.put("useChartRecognition", USE_CHART_RECOGNITION);
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
