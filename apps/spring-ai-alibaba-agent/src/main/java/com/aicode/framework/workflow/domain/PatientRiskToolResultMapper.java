package com.aicode.framework.workflow.domain;

import com.aicode.core.domain.exception.ToolExecutionException;
import com.aicode.framework.workflow.domain.model.HealthMetrics;
import com.aicode.framework.workflow.domain.model.PatientProfile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 把 {@code ToolPort} 返回的 JSON 字符串解析为领域值对象，并组装工具入参 JSON。
 * 无状态，避免 Workflow 内散落 Jackson 解析细节。
 */
public class PatientRiskToolResultMapper {

    private final ObjectMapper objectMapper;

    public PatientRiskToolResultMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 组装单参数（patientId）工具入参 JSON。
     */
    public String arguments(String patientId) {
        try {
            return objectMapper.writeValueAsString(Map.of("patientId", patientId));
        } catch (JsonProcessingException ex) {
            throw new ToolExecutionException("failed to serialize tool arguments", ex);
        }
    }

    /**
     * 解析患者工具结果为 {@link PatientProfile}。
     */
    public PatientProfile parsePatient(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return new PatientProfile(
                    node.path("patientId").asText(),
                    node.path("name").asText(),
                    node.path("age").asInt(),
                    node.path("gender").asText(),
                    node.path("diagnosis").asText());
        } catch (JsonProcessingException ex) {
            throw new ToolExecutionException("failed to parse patient tool result", ex);
        }
    }

    /**
     * 解析健康指标工具结果为 {@link HealthMetrics}。
     */
    public HealthMetrics parseMetrics(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return new HealthMetrics(
                    node.path("systolic").asDouble(),
                    node.path("diastolic").asDouble(),
                    node.path("fastingGlucose").asDouble(),
                    node.path("hba1c").asDouble());
        } catch (JsonProcessingException ex) {
            throw new ToolExecutionException("failed to parse metrics tool result", ex);
        }
    }
}
