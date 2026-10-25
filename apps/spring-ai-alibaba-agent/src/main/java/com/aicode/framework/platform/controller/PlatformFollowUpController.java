package com.aicode.framework.platform.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.framework.dto.ApiResponse;
import com.aicode.framework.followup.application.FollowUpReminderView;
import com.aicode.framework.followup.application.FollowUpTaskActionUseCase;
import com.aicode.framework.followup.application.FollowUpTaskUseCase;
import com.aicode.framework.followup.domain.model.FollowUpStatus;
import com.aicode.framework.followup.domain.model.FollowUpTask;
import com.aicode.framework.followup.dto.FollowUpReminderResponse;
import com.aicode.framework.followup.dto.FollowUpTaskCancelRequest;
import com.aicode.framework.followup.dto.FollowUpTaskCompleteRequest;
import com.aicode.framework.followup.dto.FollowUpTaskListResponse;
import com.aicode.framework.followup.dto.FollowUpTaskResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * 随访任务 API（Week 24）。
 *
 * <p>普通用户只能看/操作自己的任务；admin 可带 {@code userId} 查看他人。
 * 完成/取消是原子状态迁移，重复操作返回 409。</p>
 */
@RestController
@RequestMapping("/api/v1/platform/followup-tasks")
public class PlatformFollowUpController {

    private final FollowUpTaskUseCase followUpTaskUseCase;
    private final FollowUpTaskActionUseCase followUpTaskActionUseCase;
    private final Clock clock;

    public PlatformFollowUpController(
            FollowUpTaskUseCase followUpTaskUseCase,
            FollowUpTaskActionUseCase followUpTaskActionUseCase,
            Clock clock
    ) {
        this.followUpTaskUseCase = followUpTaskUseCase;
        this.followUpTaskActionUseCase = followUpTaskActionUseCase;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /**
     * 任务列表。
     *
     * @param status    状态：PENDING（默认）/ COMPLETED / CANCELLED；非法值 400
     * @param patientId 患者过滤
     * @param userId    仅 admin 可传（查他人）
     * @param limit     条数上限，默认 20、上限 100
     * @return 任务列表
     */
    @GetMapping
    public ApiResponse<FollowUpTaskListResponse> list(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        StpUtil.checkLogin();
        long callerUserId = StpUtil.getLoginIdAsLong();
        List<FollowUpTask> tasks = followUpTaskUseCase.list(
                callerUserId, userId, parseStatus(status), patientId, limit);
        return ApiResponse.success(FollowUpTaskListResponse.from(tasks, clock.instant()));
    }

    /**
     * 任务详情（本人或 admin）。
     *
     * @param taskId 任务 ID
     * @return 任务
     */
    @GetMapping("/{taskId}")
    public ApiResponse<FollowUpTaskResponse> get(@PathVariable String taskId) {
        StpUtil.checkLogin();
        long callerUserId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(
                FollowUpTaskResponse.from(followUpTaskUseCase.get(callerUserId, taskId), clock.instant()));
    }

    /**
     * 完成任务。
     *
     * @param taskId  任务 ID
     * @param request 备注
     * @return 完成后的任务
     */
    @PostMapping("/{taskId}/complete")
    public ApiResponse<FollowUpTaskResponse> complete(
            @PathVariable String taskId,
            @Valid @RequestBody FollowUpTaskCompleteRequest request
    ) {
        StpUtil.checkLogin();
        long callerUserId = StpUtil.getLoginIdAsLong();
        Instant now = clock.instant();
        return ApiResponse.success(FollowUpTaskResponse.from(
                followUpTaskActionUseCase.complete(callerUserId, taskId, request.note()), now));
    }

    /**
     * 取消任务。
     *
     * @param taskId  任务 ID
     * @param request 原因
     * @return 取消后的任务
     */
    @PostMapping("/{taskId}/cancel")
    public ApiResponse<FollowUpTaskResponse> cancel(
            @PathVariable String taskId,
            @Valid @RequestBody FollowUpTaskCancelRequest request
    ) {
        StpUtil.checkLogin();
        long callerUserId = StpUtil.getLoginIdAsLong();
        Instant now = clock.instant();
        return ApiResponse.success(FollowUpTaskResponse.from(
                followUpTaskActionUseCase.cancel(callerUserId, taskId, request.reason()), now));
    }

    /**
     * 站内提醒：逾期 + 未来 N 天内到期。
     *
     * @param days 未来天数，默认 7、上限 30
     * @return 提醒视图
     */
    @GetMapping("/reminders")
    public ApiResponse<FollowUpReminderResponse> reminders(
            @RequestParam(defaultValue = "7") int days
    ) {
        StpUtil.checkLogin();
        long callerUserId = StpUtil.getLoginIdAsLong();
        FollowUpReminderView view = followUpTaskUseCase.reminders(callerUserId, days);
        return ApiResponse.success(FollowUpReminderResponse.from(view, clock.instant()));
    }

    private FollowUpStatus parseStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        try {
            return FollowUpStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new InvalidChatRequestException("status must be one of PENDING, COMPLETED, CANCELLED");
        }
    }
}
