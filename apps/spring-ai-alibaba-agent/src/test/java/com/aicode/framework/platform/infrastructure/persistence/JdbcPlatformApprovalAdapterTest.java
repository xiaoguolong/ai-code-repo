package com.aicode.framework.platform.infrastructure.persistence;

import com.aicode.framework.platform.domain.model.ApprovalStatus;
import com.aicode.framework.platform.domain.model.PlatformApproval;
import com.aicode.framework.platform.domain.model.PlatformUser;
import com.aicode.framework.platform.domain.port.PlatformApprovalPort;
import com.aicode.framework.platform.domain.port.PlatformUserPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审批任务落库适配器（H2 MODE=PostgreSQL + Flyway V6）。
 *
 * <p>重点：{@code markDecided} 的原子性（第二次必须失败）与状态/用户过滤。</p>
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "auth.password-salt=test-salt",
        "platform.security.enforce-direct-runs=false",
        "platform.persistence.mode=jdbc",
        "platform.memory.lifecycle.cleanup-enabled=false"
})
@ActiveProfiles("test")
class JdbcPlatformApprovalAdapterTest {

    private static final long USER_A = 9801L;
    private static final long USER_B = 9802L;
    private static final Instant NOW = Instant.parse("2026-10-25T00:00:00Z");

    @Autowired
    private PlatformApprovalPort approvalPort;

    @Autowired
    private PlatformUserPort userPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void ensureUsers() {
        // 本类共享 H2；先清掉本类专属用户的任务，避免用例间互相污染。
        jdbcTemplate.update("DELETE FROM platform_approval WHERE user_id IN (?, ?)", USER_A, USER_B);
        // platform_approval.user_id 有外键：先保证用户存在（H2 所有测试共用一个库）。
        userPort.save(new PlatformUser(USER_A, "w23-approval-a", "hash", "operator"));
        userPort.save(new PlatformUser(USER_B, "w23-approval-b", "hash", "operator"));
    }

    @Test
    void wiresJdbcAdapterWhenModeIsJdbc() {
        assertThat(approvalPort).isInstanceOf(JdbcPlatformApprovalAdapter.class);
    }

    @Test
    void savesAndReadsApprovalWithNullDecisionFields() {
        PlatformApproval approval = approval(USER_A, ApprovalStatus.PENDING);
        approvalPort.save(approval);

        PlatformApproval loaded = approvalPort.findById(approval.approvalId()).orElseThrow();

        assertThat(loaded.executionId()).isEqualTo(approval.executionId());
        assertThat(loaded.tenantKey()).isEqualTo("w23-tenant");
        assertThat(loaded.status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(loaded.decidedBy()).isNull();
        assertThat(loaded.decidedAt()).isNull();
    }

    @Test
    void markDecidedOnlySucceedsOnce() {
        PlatformApproval approval = approval(USER_A, ApprovalStatus.PENDING);
        approvalPort.save(approval);

        boolean first = approvalPort.markDecided(
                approval.approvalId(), ApprovalStatus.APPROVED, 1L, "同意", NOW.plusSeconds(60));
        boolean second = approvalPort.markDecided(
                approval.approvalId(), ApprovalStatus.REJECTED, 2L, "再拒", NOW.plusSeconds(120));

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        PlatformApproval loaded = approvalPort.findById(approval.approvalId()).orElseThrow();
        assertThat(loaded.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(loaded.decidedBy()).isEqualTo(1L);
        assertThat(loaded.comment()).isEqualTo("同意");
        assertThat(loaded.decidedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void listsByUserAndStatusWithoutLeakingOtherUsers() {
        PlatformApproval pendingA = approval(USER_A, ApprovalStatus.PENDING);
        PlatformApproval approvedA = approval(USER_A, ApprovalStatus.APPROVED);
        PlatformApproval pendingB = approval(USER_B, ApprovalStatus.PENDING);
        approvalPort.save(pendingA);
        approvalPort.save(approvedA);
        approvalPort.save(pendingB);

        assertThat(approvalPort.listByUser(USER_A, ApprovalStatus.PENDING, 20))
                .extracting(PlatformApproval::approvalId)
                .containsExactly(pendingA.approvalId());
        assertThat(approvalPort.listByUser(USER_A, ApprovalStatus.APPROVED, 20))
                .extracting(PlatformApproval::approvalId)
                .contains(approvedA.approvalId());
        assertThat(approvalPort.listAll(ApprovalStatus.PENDING, 100))
                .extracting(PlatformApproval::approvalId)
                .contains(pendingA.approvalId(), pendingB.approvalId());
    }

    private PlatformApproval approval(long userId, ApprovalStatus status) {
        String id = UUID.randomUUID().toString();
        return new PlatformApproval(
                id,
                "exec-" + id,
                "w23-tenant",
                userId,
                "patient-analysis",
                "P002",
                "HIGH",
                "为患者 P002 生成分析报告",
                status,
                "",
                status == ApprovalStatus.PENDING ? null : 1L,
                NOW,
                status == ApprovalStatus.PENDING ? null : NOW.plusSeconds(60));
    }
}
