package com.aicode.enterprise.infrastructure.ocr;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PDF 按页分批（Week 33）：千帆单次 PDF 只解析 10 页，超过就分批送。
 *
 * <p>用 PDFBox 的 {@link Splitter} 逐页拆开，再按 {@code batchPages} 合并成小 PDF；
 * 图片/非 PDF 不经过这里。</p>
 */
@Component
public class PdfPageSplitter {

    /**
     * 判断是否按 PDF 处理。
     *
     * @param fileName    原始文件名
     * @param contentType 内容类型
     * @return 后缀 .pdf 或 content-type 含 pdf
     */
    public boolean isPdf(String fileName, String contentType) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return name.endsWith(".pdf") || type.contains("pdf");
    }

    /**
     * 拆分 PDF。
     *
     * @param pdf        原文件字节
     * @param batchPages 每批页数（≥1）
     * @return 分批结果（chunks 与每批页数一一对应）
     * @throws IllegalStateException 读取/拆分失败
     */
    public Batches split(byte[] pdf, int batchPages) {
        if (batchPages < 1) {
            throw new IllegalArgumentException("batchPages must be >= 1");
        }
        try (PDDocument source = Loader.loadPDF(pdf)) {
            int totalPages = source.getNumberOfPages();
            if (totalPages == 0) {
                throw new IllegalStateException("PDF has no pages");
            }
            List<PDDocument> pages = new Splitter().split(source);
            List<byte[]> chunks = new ArrayList<>();
            List<Integer> counts = new ArrayList<>();
            try {
                for (int start = 0; start < pages.size(); start += batchPages) {
                    int end = Math.min(start + batchPages, pages.size());
                    try (PDDocument batch = new PDDocument()) {
                        PDFMergerUtility merger = new PDFMergerUtility();
                        for (int i = start; i < end; i++) {
                            merger.appendDocument(batch, pages.get(i));
                        }
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        batch.save(out);
                        chunks.add(out.toByteArray());
                        counts.add(end - start);
                    }
                }
            } finally {
                for (PDDocument page : pages) {
                    page.close();
                }
            }
            return new Batches(List.copyOf(chunks), List.copyOf(counts), totalPages);
        } catch (IOException ex) {
            throw new IllegalStateException("PDF split failed", ex);
        }
    }

    /**
     * 分批结果。
     *
     * @param chunks     每批 PDF 字节
     * @param counts     每批页数
     * @param totalPages 原文件总页数
     */
    public record Batches(List<byte[]> chunks, List<Integer> counts, int totalPages) {
    }
}
