package com.aicode.enterprise.dto;

import com.aicode.enterprise.domain.model.SsoUserLink;

import java.util.List;

/**
 * SSO 映射列表响应（Week 23）。
 *
 * @param items 映射列表
 * @param total 当前页条数
 */
public record SsoLinkListResponse(List<SsoLinkResponse> items, int total) {

    /**
     * 由领域模型列表转换。
     *
     * @param links 映射列表
     * @return 响应体
     */
    public static SsoLinkListResponse from(List<SsoUserLink> links) {
        List<SsoLinkResponse> items = links.stream().map(SsoLinkResponse::from).toList();
        return new SsoLinkListResponse(items, items.size());
    }
}
