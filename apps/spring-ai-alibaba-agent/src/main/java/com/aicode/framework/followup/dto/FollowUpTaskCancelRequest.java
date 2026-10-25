package com.aicode.framework.followup.dto;

import jakarta.validation.constraints.Size;

/**
 * 取消任务请求（Week 24）。
 *
 * @param reason 取消原因，可空，最长 500
 */
public record FollowUpTaskCancelRequest(
        @Size(max = 500, message = "reason must not exceed 500 characters") String reason
) {
}
