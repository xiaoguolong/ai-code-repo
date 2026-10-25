package com.aicode.framework.followup.dto;

import jakarta.validation.constraints.Size;

/**
 * 完成任务请求（Week 24）。
 *
 * @param note 完成备注，可空，最长 500
 */
public record FollowUpTaskCompleteRequest(
        @Size(max = 500, message = "note must not exceed 500 characters") String note
) {
}
