package com.aicode.framework.platform.application;

import com.aicode.framework.platform.domain.exception.PlatformConflictException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.AgentType;
import com.aicode.framework.platform.domain.model.PlatformAgentConfig;
import com.aicode.framework.platform.infrastructure.persistence.InMemoryAgentRegistryAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 平台 Agent 用例测试。 */
class PlatformAgentUseCaseTest {

    private PlatformAgentUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PlatformAgentUseCase(new InMemoryAgentRegistryAdapter());
    }

    @Test
    void registersAndListsAgent() {
        useCase.register("demo", "Demo Agent", "desc", AgentType.FRAMEWORK_REACT);

        assertThat(useCase.listAgents()).hasSize(1);
        assertThat(useCase.getAgent("demo").name()).isEqualTo("Demo Agent");
    }

    @Test
    void rejectsDuplicateAgentKey() {
        useCase.register("demo", "Demo", "desc", AgentType.FRAMEWORK_REACT);

        assertThatThrownBy(() -> useCase.register("demo", "Dup", "desc", AgentType.FRAMEWORK_REACT))
                .isInstanceOf(PlatformConflictException.class);
    }

    @Test
    void updatesAgentConfig() {
        useCase.register("demo", "Demo", "desc", AgentType.MEDICAL_ASSISTANT);

        var updated = useCase.updateConfig("demo",
                new PlatformAgentConfig(false, 3, 0.1, "test-model"));

        assertThat(updated.config().enabled()).isFalse();
        assertThat(updated.config().maxIterations()).isEqualTo(3);
    }

    @Test
    void getAgentFailsWhenMissing() {
        assertThatThrownBy(() -> useCase.getAgent("missing"))
                .isInstanceOf(PlatformNotFoundException.class);
    }
}
