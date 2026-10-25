package com.aicode.framework.platform.dto;

import com.aicode.framework.platform.application.PlatformUserView;

import java.util.List;

/**
 * 平台用户列表响应（Week 28）。
 *
 * @param total 条数
 * @param items 用户列表（按 userId 升序）
 */
public record PlatformUserListResponse(int total, List<PlatformUserResponse> items) {

    /**
     * 由视图列表转换。
     *
     * @param views 用户视图
     * @return 响应体
     */
    public static PlatformUserListResponse from(List<PlatformUserView> views) {
        List<PlatformUserResponse> items = views == null
                ? List.of()
                : views.stream().map(PlatformUserResponse::from).toList();
        return new PlatformUserListResponse(items.size(), items);
    }
}
