package com.aicode.enterprise.infrastructure.ocr;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PDF 分批拆分测试（Week 33）。
 */
class PdfPageSplitterTest {

    private final PdfPageSplitter splitter = new PdfPageSplitter();

    @Test
    void splitsTwentyPagesIntoBatchesOfEight() throws Exception {
        byte[] pdf = pdfWithPages(20);

        PdfPageSplitter.Batches batches = splitter.split(pdf, 8);

        assertThat(batches.totalPages()).isEqualTo(20);
        assertThat(batches.counts()).containsExactly(8, 8, 4);
        assertThat(batches.chunks()).hasSize(3);
        for (int i = 0; i < batches.chunks().size(); i++) {
            try (PDDocument doc = Loader.loadPDF(batches.chunks().get(i))) {
                assertThat(doc.getNumberOfPages()).isEqualTo(batches.counts().get(i));
            }
        }
    }

    @Test
    void keepsSmallPdfInSingleBatch() throws Exception {
        PdfPageSplitter.Batches batches = splitter.split(pdfWithPages(3), 8);

        assertThat(batches.counts()).containsExactly(3);
    }

    @Test
    void detectsPdfByExtensionOrContentType() {
        assertThat(splitter.isPdf("a.PDF", "application/octet-stream")).isTrue();
        assertThat(splitter.isPdf("a.bin", "application/pdf")).isTrue();
        assertThat(splitter.isPdf("a.png", "image/png")).isFalse();
        assertThat(splitter.isPdf(null, null)).isFalse();
    }

    @Test
    void rejectsEmptyPdf() {
        assertThatThrownBy(() -> splitter.split("not-pdf".getBytes(StandardCharsets.UTF_8), 8))
                .isInstanceOf(IllegalStateException.class);
    }

    private static byte[] pdfWithPages(int pages) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new PDPage());
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
