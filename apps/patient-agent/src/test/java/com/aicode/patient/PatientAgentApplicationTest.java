package com.aicode.patient;

import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.port.LongTermMemoryPort;
import com.aicode.core.domain.port.MemoryPort;
import com.aicode.core.domain.port.ToolPort;
import com.aicode.patient.domain.ReActAgent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 应用上下文冒烟测试：验证 ai-core 装配 + 工具注册表 + 4 个业务工具 + 记忆端口 + ReActAgent 完整启动。
 */
@SpringBootTest
class PatientAgentApplicationTest {

    @Autowired
    private ToolPort toolPort;

    @Autowired
    private ReActAgent reActAgent;

    @Autowired
    private MemoryPort memoryPort;

    @Autowired
    private LongTermMemoryPort longTermMemoryPort;

    @Test
    void contextLoadsAndWiresFourTools() {
        assertThat(reActAgent).isNotNull();
        assertThat(memoryPort).isNotNull();
        assertThat(longTermMemoryPort).isNotNull();

        List<String> names = toolPort.definitions().stream()
                .map(ToolDefinition::name)
                .sorted()
                .toList();
        assertThat(names).containsExactly("HealthDataTool", "OrderTool", "PatientTool", "ReportTool");
    }
}
