package com.aicode.enterprise.domain;

import com.aicode.enterprise.domain.model.FileReference;
import com.aicode.enterprise.domain.model.OcrResult;
import com.aicode.enterprise.domain.model.StorageType;
import com.aicode.enterprise.domain.port.FileStoragePort;
import com.aicode.enterprise.domain.port.ImageDownloadPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OCR 图片占位符清理器单元测试。
 */
class OcrMarkdownImageProcessorTest {

    private ImageDownloadPort downloadPort;
    private FileStoragePort storagePort;
    private OcrMarkdownImageProcessor processor;

    @BeforeEach
    void setUp() {
        downloadPort = mock(ImageDownloadPort.class);
        storagePort = mock(FileStoragePort.class);
        FileUrlAssembler assembler = new FileUrlAssembler("http://localhost:8081");
        processor = new OcrMarkdownImageProcessor(downloadPort, storagePort, assembler);
    }

    @Test
    void replacesImagePlaceholderWithAccessUrl() {
        byte[] bytes = {1, 2, 3};
        when(downloadPort.download("http://tmp/img.jpg")).thenReturn(bytes);
        when(storagePort.store(eq(1L), eq("imgs/img.jpg"), isNull(), any(byte[].class)))
                .thenReturn(new FileReference("fid-1", "imgs/img.jpg", null, 3, StorageType.LOCAL,
                        "http://localhost:8081/api/v1/files/fid-1"));

        OcrResult result = new OcrResult(
                "# 标题\n\n<div><img src=\"imgs/img.jpg\"></div>",
                Map.of("imgs/img.jpg", "http://tmp/img.jpg")
        );

        String cleaned = processor.clean(result, 1L);

        assertThat(cleaned).contains("![img](http://localhost:8081/api/v1/files/fid-1)");
        assertThat(cleaned).doesNotContain("<img");
    }

    @Test
    void keepsPlaceholderWhenImageUrlMissing() {
        OcrResult result = new OcrResult(
                "<div><img src=\"imgs/missing.jpg\"></div>",
                Map.of()
        );

        String cleaned = processor.clean(result, 1L);

        assertThat(cleaned).contains("<img src=\"imgs/missing.jpg\">");
    }

    @Test
    void keepsPlaceholderWhenDownloadFails() {
        when(downloadPort.download("http://tmp/img.jpg")).thenThrow(new RuntimeException("boom"));
        OcrResult result = new OcrResult(
                "<div><img src=\"imgs/img.jpg\"></div>",
                Map.of("imgs/img.jpg", "http://tmp/img.jpg")
        );

        String cleaned = processor.clean(result, 1L);

        assertThat(cleaned).contains("<img src=\"imgs/img.jpg\">");
    }

    @Test
    void replacesDivWrappedPlaceholderWithoutLeavingDiv() {
        byte[] bytes = {1, 2, 3};
        when(downloadPort.download("http://tmp/img.jpg")).thenReturn(bytes);
        when(storagePort.store(eq(1L), eq("imgs/img.jpg"), isNull(), any(byte[].class)))
                .thenReturn(new FileReference("fid-1", "imgs/img.jpg", null, 3, StorageType.LOCAL,
                        "http://localhost:8081/api/v1/files/fid-1"));

        OcrResult result = new OcrResult(
                "# 标题\n\n<div style=\"text-align: center;\"><img src=\"imgs/img.jpg\" alt=\"Image\" width=\"61%\" /></div>\n\n正文",
                Map.of("imgs/img.jpg", "http://tmp/img.jpg")
        );

        String cleaned = processor.clean(result, 1L);

        assertThat(cleaned).contains("![img](http://localhost:8081/api/v1/files/fid-1)");
        assertThat(cleaned).doesNotContain("<img").doesNotContain("<div").doesNotContain("</div>");
    }

    @Test
    void returnsOriginalWhenNoPlaceholder() {
        OcrResult result = new OcrResult("# 纯文本", Map.of());

        assertThat(processor.clean(result, 1L)).isEqualTo("# 纯文本");
    }
}
