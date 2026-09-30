package com.aicode.framework.platform.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.framework.platform.domain.exception.PlatformAgentDisabledException;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.ExecutionRecord;
import com.aicode.framework.platform.domain.model.ExecutionStatus;
import com.aicode.framework.platform.domain.model.PlatformAgentDefinition;
import com.aicode.framework.platform.domain.model.PlatformRunOutput;
import com.aicode.framework.platform.domain.port.AgentRegistryPort;
import com.aicode.framework.platform.domain.port.ExecutionRecordPort;
import com.aicode.framework.platform.domain.service.PlatformAgentRunner;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 平台执行记录与 Agent 调度用例。
 */
@Service
public class PlatformExecutionUseCase {

    private static final Logger log = LoggerFactory.getLogger(PlatformExecutionUseCase.class);

    private final AgentRegistryPort agentRegistryPort;
    private final ExecutionRecordPort executionRecordPort;
    private final PlatformAgentRunner platformAgentRunner;
    private final ObjectMapper objectMapper;

    public PlatformExecutionUseCase(
            AgentRegistryPort agentRegistryPort,
            ExecutionRecordPort executionRecordPort,
            PlatformAgentRunner platformAgentRunner,
            ObjectMapper objectMapper
    ) {
        this.agentRegistryPort = agentRegistryPort;
        this.executionRecordPort = executionRecordPort;
        this.platformAgentRunner = platformAgentRunner;
        this.objectMapper = objectMapper;
    }

    /** 经平台调度 Agent 并写入执行记录。 */
    public ExecutionRecord runAgent(String agentKey, Map<String, Object> input) {
        PlatformAgentDefinition agent = agentRegistryPort.findByKey(requireKey(agentKey))
                .orElseThrow(() -> new PlatformNotFoundException("agent not found: " + agentKey));
        if (!agent.config().enabled()) {
            throw new PlatformAgentDisabledException("agent is disabled: " + agentKey);
        }

        String executionId = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();
        ExecutionRecord running = new ExecutionRecord(
                executionId, agent.agentKey(), agent.agentType(), ExecutionStatus.RUNNING,
                toJson(input), "", "", TokenUsage.unknown(), "", startedAt, null);
        executionRecordPort.save(running);
        log.info("[platform] execution started executionId={} agentKey={} agentType={} input={}",
                executionId, agent.agentKey(), agent.agentType(), running.inputJson());

        try {
            PlatformRunOutput output = platformAgentRunner.run(agent, input);
            ExecutionRecord completed = new ExecutionRecord(
                    executionId, agent.agentKey(), agent.agentType(), ExecutionStatus.COMPLETED,
                    running.inputJson(), toJson(output.output()), output.model(), output.usage(),
                    "", startedAt, Instant.now());
            executionRecordPort.save(completed);
            log.info("[platform] execution completed executionId={} agentKey={} model={} tokens={}",
                    executionId, agent.agentKey(), output.model(), output.usage().totalTokens());
            return completed;
        } catch (RuntimeException ex) {
            ExecutionRecord failed = new ExecutionRecord(
                    executionId, agent.agentKey(), agent.agentType(), ExecutionStatus.FAILED,
                    running.inputJson(), "", "", TokenUsage.unknown(), ex.getMessage(),
                    startedAt, Instant.now());
            executionRecordPort.save(failed);
            log.warn("[platform] execution failed executionId={} agentKey={} error={}",
                    executionId, agent.agentKey(), ex.getMessage());
            throw ex;
        }
    }

    /** 列出执行记录。 */
    public List<ExecutionRecord> listExecutions() {
        return executionRecordPort.listAll();
    }

    /** 查询执行记录。 */
    public ExecutionRecord getExecution(String executionId) {
        return executionRecordPort.findById(requireKey(executionId))
                .orElseThrow(() -> new PlatformNotFoundException("execution not found: " + executionId));
    }

    private String requireKey(String value) {
        String key = value == null ? "" : value.trim();
        if (key.isEmpty()) {
            throw new InvalidChatRequestException("id must not be blank");
        }
        return key;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize json", ex);
        }
    }
}
