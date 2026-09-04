package com.aicode.enterprise.domain.port;

import com.aicode.enterprise.domain.exception.FileStorageException;

/**
 * 出站端口：下载远程图片字节。OCR 图片占位符清理时，把厂商临时图片下载后转存到文件服务。
 */
public interface ImageDownloadPort {

    /**
     * 下载远程图片。
     *
     * @param url 图片 URL
     * @return 图片字节
     * @throws FileStorageException 下载失败
     */
    byte[] download(String url);
}
