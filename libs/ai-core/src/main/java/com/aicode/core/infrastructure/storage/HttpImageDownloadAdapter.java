package com.aicode.core.infrastructure.storage;

import com.aicode.core.domain.exception.FileStorageException;
import com.aicode.core.domain.port.ImageDownloadPort;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 基于 JDK HttpClient 的图片下载适配器。用于 OCR 占位符清理时拉取厂商临时图片。
 */
@Component
public class HttpImageDownloadAdapter implements ImageDownloadPort {

    private final HttpClient httpClient;

    public HttpImageDownloadAdapter() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public byte[] download(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                throw new FileStorageException("image download HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException ex) {
            throw new FileStorageException("image download failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new FileStorageException("image download interrupted", ex);
        }
    }
}
