package com.aicode.framework.platform.controller;

import com.aicode.framework.platform.application.PlatformAgentUseCase;
import com.aicode.framework.platform.domain.model.AgentType;
import com.aicode.framework.platform.domain.model.PlatformAgentConfig;
import com.aicode.framework.platform.domain.model.PlatformAgentDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 平台 Agent 控制器契约测试。 */
@WebMvcTest(PlatformAgentController.class)
class PlatformAgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlatformAgentUseCase platformAgentUseCase;

    @Test
    void listsAgents() throws Exception {
        when(platformAgentUseCase.listAgents()).thenReturn(List.of(sampleAgent()));

        mockMvc.perform(get("/api/v1/platform/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].agentKey").value("framework-react"));
    }

    @Test
    void registersAgent() throws Exception {
        when(platformAgentUseCase.register(any(), any(), any(), eq(AgentType.FRAMEWORK_REACT)))
                .thenReturn(sampleAgent());

        mockMvc.perform(post("/api/v1/platform/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentKey": "framework-react",
                                  "name": "Graph Agent",
                                  "description": "Week 8",
                                  "agentType": "FRAMEWORK_REACT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentType").value("FRAMEWORK_REACT"));
    }

    private PlatformAgentDefinition sampleAgent() {
        return new PlatformAgentDefinition(
                "framework-react", "Graph Agent", "Week 8",
                AgentType.FRAMEWORK_REACT, PlatformAgentConfig.defaults(), Instant.now());
    }
}
