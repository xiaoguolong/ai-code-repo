package com.aicode.demo.application;

import com.aicode.demo.domain.exception.InvalidChatRequestException;
import com.aicode.demo.domain.model.OcrFileType;
import com.aicode.demo.domain.port.DocumentOcrPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * OCR 识别用例：校验入参后委派给端口。
 */
@ExtendWith(MockitoExtension.class)
class RecognizeOcrUseCaseTest {

    @Mock
    private DocumentOcrPort ocrPort;

    private RecognizeOcrUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RecognizeOcrUseCase(ocrPort);
    }

    @Test
    void shouldRecognize_andReturnText() {
        when(ocrPort.recognize("https://example.com/a.jpg", OcrFileType.IMAGE)).thenReturn("识别文本");

        assertThat(useCase.recognize("https://example.com/a.jpg", OcrFileType.IMAGE)).isEqualTo("识别文本");
    }

    @Test
    void shouldRejectBlankFile() {
        assertThatThrownBy(() -> useCase.recognize("   ", null))
                .isInstanceOf(InvalidChatRequestException.class);
    }
}
