package com.aicode.framework.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 错误码规范契约：命名、唯一性、HTTP 状态与中文说明，缺一不可。
 */
class ApiErrorCodeContractTest {

    private static final Pattern UPPER_SNAKE = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    @ParameterizedTest
    @EnumSource(ApiErrorCode.class)
    void codeNameIsUpperSnakeCaseAndReasonablyShort(ApiErrorCode code) {
        assertThat(code.name()).matches(UPPER_SNAKE);
        assertThat(code.name().length()).isLessThanOrEqualTo(40);
    }

    @ParameterizedTest
    @EnumSource(ApiErrorCode.class)
    void codeCarriesHttpStatusAndChineseMessage(ApiErrorCode code) {
        assertThat(code.status()).isNotNull();
        assertThat(code.message()).isNotBlank();
        assertThat(code.message()).containsPattern("[\\u4e00-\\u9fa5]");
        assertThat(code.message()).doesNotContain("Exception").doesNotContain("null");
    }

    @ParameterizedTest
    @EnumSource(ApiErrorCode.class)
    void codeValuesAreUnique(ApiErrorCode code) {
        long sameName = Arrays.stream(ApiErrorCode.values())
                .filter(candidate -> candidate.name().equals(code.name()))
                .count();
        assertThat(sameName).isEqualTo(1);
    }

    @Test
    void errorCodesNeverUseSuccessReservedName() {
        assertThat(Arrays.stream(ApiErrorCode.values()).map(Enum::name))
                .doesNotContain("SUCCESS");
    }

    @Test
    void statusUsesSemanticHttpCodesOnly() {
        Set<HttpStatus> allowed = Set.of(
                HttpStatus.BAD_REQUEST,
                HttpStatus.UNAUTHORIZED,
                HttpStatus.FORBIDDEN,
                HttpStatus.NOT_FOUND,
                HttpStatus.CONFLICT,
                HttpStatus.BAD_GATEWAY,
                HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(Arrays.stream(ApiErrorCode.values()).map(ApiErrorCode::status))
                .allSatisfy(status -> assertThat(allowed).contains(status));
    }

    @Test
    void mapsEveryDomainFailureCodeTheApiLayerReliesOn() {
        List<String> required = List.of(
                "VALIDATION_ERROR", "UNAUTHORIZED", "FORBIDDEN", "GUARDRAIL_VIOLATION",
                "PLATFORM_NOT_FOUND", "PLATFORM_CONFLICT", "AGENT_DISABLED",
                "CHAT_MODEL_ERROR", "TOOL_EXECUTION_ERROR", "AGENT_LOOP_EXCEEDED",
                "AGENT_EXECUTION_ERROR", "WORKFLOW_NOT_FOUND", "WORKFLOW_NOT_PENDING",
                "INTERNAL_ERROR");

        assertThat(Arrays.stream(ApiErrorCode.values()).map(Enum::name).collect(Collectors.toSet()))
                .containsAll(required);
    }
}
