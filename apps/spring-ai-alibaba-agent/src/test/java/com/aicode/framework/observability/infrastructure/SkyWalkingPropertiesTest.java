package com.aicode.framework.observability.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SkyWalking 配置归一化测试（Week 19）。
 */
class SkyWalkingPropertiesTest {

    @Test
    @DisplayName("空值兜底：服务名 unknown-service、后端空串、标签前缀 aicode")
    void blankValuesFallBack() {
        SkyWalkingProperties properties = new SkyWalkingProperties(false, "  ", null, "");

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.resolvedServiceName()).isEqualTo("unknown-service");
        assertThat(properties.resolvedBackendService()).isEmpty();
        assertThat(properties.resolvedTagPrefix()).isEqualTo("aicode");
    }

    @Test
    @DisplayName("显式配置原样使用，并去掉首尾空白")
    void explicitValuesAreTrimmed() {
        SkyWalkingProperties properties = new SkyWalkingProperties(
                true, " ai-code-platform ", " 192.168.132.128:11800 ", " myorg ");

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.resolvedServiceName()).isEqualTo("ai-code-platform");
        assertThat(properties.resolvedBackendService()).isEqualTo("192.168.132.128:11800");
        assertThat(properties.resolvedTagPrefix()).isEqualTo("myorg");
    }
}
