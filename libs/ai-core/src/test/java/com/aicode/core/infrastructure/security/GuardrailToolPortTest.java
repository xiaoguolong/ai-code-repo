package com.aicode.core.infrastructure.security;

import com.aicode.core.domain.exception.GuardrailViolationException;
import com.aicode.core.domain.model.ToolCall;
import com.aicode.core.domain.model.ToolDefinition;
import com.aicode.core.domain.model.ToolResult;
import com.aicode.core.domain.port.ToolPort;
import com.aicode.core.infrastructure.config.GuardrailProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** GuardrailToolPort 装饰器测试。 */
@ExtendWith(MockitoExtension.class)
class GuardrailToolPortTest {

    @Mock
    private ToolPort delegate;

    private GuardrailToolPort guardrailToolPort;
    private DefaultGuardrailAdapter guardrail;

    @BeforeEach
    void setUp() {
        guardrail = new DefaultGuardrailAdapter(
                new GuardrailProperties(true, 8192, true, true, List.of("patientId")));
        guardrailToolPort = new GuardrailToolPort(delegate, guardrail, new ObjectMapper());
    }

    @Test
    void rejectsDisallowedArgumentKeyBeforeDelegate() {
        ToolCall call = new ToolCall("call-1", "PatientLookupTool", "{\"patientId\":\"P001\",\"hack\":true}");

        assertThatThrownBy(() -> guardrailToolPort.execute(call))
                .isInstanceOf(GuardrailViolationException.class);
    }

    @Test
    void sanitizesToolOutputFromDelegate() throws Exception {
        ToolCall call = new ToolCall("call-1", "PatientLookupTool", "{\"patientId\":\"P001\"}");
        when(delegate.execute(any())).thenReturn(new ToolResult("{\"phone\":\"13812345678\"}"));

        ToolResult result = guardrailToolPort.execute(call);

        assertThat(result.output()).contains("138****5678");
        verify(delegate).execute(call);
    }

    @Test
    void delegatesDefinitions() {
        when(delegate.definitions()).thenReturn(List.of(new ToolDefinition("T", "d", Map.of())));

        assertThat(guardrailToolPort.definitions()).hasSize(1);
    }
}
