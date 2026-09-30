package com.aicode.framework;

import com.aicode.core.domain.port.ChatModelPort;
import com.aicode.core.domain.port.ToolPort;
import com.aicode.framework.domain.FrameworkAgentGraph;
import com.aicode.framework.multiagent.application.MedicalAssistantUseCase;
import com.aicode.framework.multiagent.domain.MedicalAssistantSupervisor;
import com.aicode.framework.multiagent.domain.MedicalAssistantSupervisorGraph;
import com.aicode.framework.platform.application.PlatformAgentUseCase;
import com.aicode.framework.platform.application.PlatformExecutionUseCase;
import com.aicode.framework.platform.domain.port.AgentRegistryPort;
import com.aicode.framework.platform.domain.port.ExecutionRecordPort;
import com.aicode.framework.platform.domain.service.PlatformAgentRunner;
import com.aicode.framework.workflow.application.PatientRiskUseCase;
import com.aicode.framework.workflow.domain.PatientRiskAssessor;
import com.aicode.framework.workflow.domain.PatientRiskWorkflow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.ai.tool.ToolCallbackProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 启动装配测试：Spring AI 适配器、Graph 领域服务、工具注册表与 MCP 工具提供者均装配成功。
 * 使用测试密钥，不发起真实网络调用。
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "framework.model-provider=spring-ai",
        "framework.agent.max-iterations=3",
        "llm.model=deepseek-chat",
        "llm.max-tokens=128",
        "llm.temperature=0.0",
        "auth.password-salt=test-salt",
        "platform.security.enforce-direct-runs=false"
})
class SpringAiAlibabaAgentApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void wiresFrameworkBeans() {
        assertThat(context.getBean(ChatModelPort.class)).isNotNull();
        assertThat(context.getBean(ToolPort.class).definitions()).hasSize(2);
        assertThat(context.getBean(FrameworkAgentGraph.class)).isNotNull();
        assertThat(context.getBean(ToolCallbackProvider.class).getToolCallbacks()).hasSize(2);
        assertThat(context.getBean(PatientRiskAssessor.class)).isNotNull();
        assertThat(context.getBean(PatientRiskWorkflow.class)).isNotNull();
        assertThat(context.getBean(PatientRiskUseCase.class)).isNotNull();
        assertThat(context.getBean(MedicalAssistantSupervisor.class)).isNotNull();
        assertThat(context.getBean(MedicalAssistantSupervisorGraph.class)).isNotNull();
        assertThat(context.getBean(MedicalAssistantUseCase.class)).isNotNull();
        assertThat(context.getBean(AgentRegistryPort.class)).isNotNull();
        assertThat(context.getBean(ExecutionRecordPort.class)).isNotNull();
        assertThat(context.getBean(PlatformAgentRunner.class)).isNotNull();
        assertThat(context.getBean(PlatformAgentUseCase.class)).isNotNull();
        assertThat(context.getBean(PlatformExecutionUseCase.class)).isNotNull();
    }
}
