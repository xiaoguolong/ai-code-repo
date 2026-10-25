package com.aicode.framework.platform.infrastructure.scheduling;

import com.aicode.framework.platform.application.ApprovalReminderUseCase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审批超时提醒调度（Week 27）：只调用用例；失败不影响应用。
 */
class ApprovalReminderSchedulerTest {

    @Test
    void delegatesToUseCase() {
        ApprovalReminderUseCase useCase = mock(ApprovalReminderUseCase.class);
        when(useCase.remindDue()).thenReturn(2);

        new ApprovalReminderScheduler(useCase).remind();

        verify(useCase).remindDue();
    }

    @Test
    void swallowsFailure() {
        ApprovalReminderUseCase useCase = mock(ApprovalReminderUseCase.class);
        when(useCase.remindDue()).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> new ApprovalReminderScheduler(useCase).remind())
                .doesNotThrowAnyException();
    }
}
