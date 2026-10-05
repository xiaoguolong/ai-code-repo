package com.aicode.framework.observability;

import com.aicode.framework.observability.domain.ModelPrice;
import com.aicode.framework.observability.domain.ModelPriceCatalog;
import com.aicode.framework.observability.infrastructure.LangfuseProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 出厂配置测试（Week 18）：保证 {@code application.yml} 里的模型价目表**默认可用**，且可被覆盖。
 *
 * <p>为什么值得单测：价目表是「成本有没有数字」的唯一开关。曾经的情形是它只有注释示例，
 * 于是每个环境都要在启动参数里手写两行 {@code --langfuse.model-prices...}，忘了就静默没有成本。
 * 这里把「出厂即有、且环境变量/属性可覆盖」固化成回归防线。</p>
 */
class LangfuseConfigDefaultsTest {

    /** 默认（不覆盖）：出厂 yml 就应提供两个演示模型的单价。 */
    @SpringBootTest(properties = {
            "spring.ai.openai.api-key=test-key",
            "auth.password-salt=test-salt",
            "platform.persistence.mode=memory",
            "management.otlp.tracing.export.enabled=false"
    })
    @ActiveProfiles("test")
    static class Defaults {

        @Autowired
        private LangfuseProperties properties;

        @Autowired
        private ModelPriceCatalog catalog;

        @Test
        void shippedApplicationYmlProvidesModelPrices() {
            assertThat(properties.modelPriceCatalog().models())
                    .contains("deepseek-v4-pro", "deepseek-chat");

            Optional<ModelPrice> price = catalog.find("deepseek-v4-pro");
            assertThat(price).isPresent();
            assertThat(price.get().inputPerMillion()).isEqualTo(0.27);
            assertThat(price.get().outputPerMillion()).isEqualTo(1.10);
        }
    }

    /** 覆盖：属性（环境变量/启动参数/yml 覆盖）优先级高于 application.yml。 */
    @SpringBootTest(properties = {
            "spring.ai.openai.api-key=test-key",
            "auth.password-salt=test-salt",
            "platform.persistence.mode=memory",
            "management.otlp.tracing.export.enabled=false",
            "langfuse.model-prices.deepseek-v4-pro.input-per-1m=1.23"
    })
    @ActiveProfiles("test")
    static class Overridden {

        @Autowired
        private ModelPriceCatalog catalog;

        @Test
        void propertyOverridesShippedPrice() {
            Optional<ModelPrice> price = catalog.find("deepseek-v4-pro");
            assertThat(price).isPresent();
            assertThat(price.get().inputPerMillion()).isEqualTo(1.23);
            // 未覆盖的一项保持出厂值
            assertThat(price.get().outputPerMillion()).isEqualTo(1.10);
        }
    }
}
