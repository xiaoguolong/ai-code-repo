package com.aicode.framework.observability.infrastructure;

import com.aicode.framework.observability.support.SkyWalkingToolkitStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SkyWalking Toolkit 反射桥测试（Week 19）。
 *
 * <p>本类用桩类替换官方 Toolkit，验证四件事：</p>
 * <ol>
 *   <li>类/方法签名匹配（真实 Toolkit 9.7.0 的签名已核实，桩与之逐一对齐）；</li>
 *   <li>建 span / 写标签 / 标错误 / 结束 span 的调用顺序与形态；</li>
 *   <li>Toolkit 抛异常时不冒泡（可观测故障不影响业务）；</li>
 *   <li>类不存在时 {@code tryCreate} 返回 null，调用方据此降级为空实现。</li>
 * </ol>
 *
 * <p>{@code available()} 的判据是「Agent 注入的 {@code skywalking.*} 系统属性存在」，
 * 因此这里显式模拟 Agent 注入的属性，与真机行为一致。</p>
 */
class ToolkitSpanBridgeTest {

    private static final String AGENT_PROPERTY = "skywalking.agent.service_name";

    private ToolkitSpanBridge bridge;

    @BeforeEach
    void setUp() {
        SkyWalkingToolkitStub.reset();
        // 模拟 Java Agent 启动时注入的系统属性（真机由 -javaagent 注入）
        System.setProperty(AGENT_PROPERTY, "ai-code-platform");
        bridge = ToolkitSpanBridge.tryCreate(
                getClass().getClassLoader(),
                SkyWalkingToolkitStub.Tracer.class.getName(),
                SkyWalkingToolkitStub.ActiveSpan.class.getName(),
                SkyWalkingToolkitStub.TraceContext.class.getName());
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(AGENT_PROPERTY);
    }

    @Test
    @DisplayName("桩类齐全时可建成真实桥，且可用性依赖 Agent 注入的系统属性")
    void bridgeIsCreatedWhenToolkitPresent() {
        assertThat(bridge).isNotNull();
        assertThat(bridge.available()).isTrue();

        // 没有 Agent 注入的属性时，即使 Toolkit 类在 classpath 上也必须如实报「不可用」
        System.clearProperty(AGENT_PROPERTY);
        assertThat(bridge.available()).isFalse();
    }

    @Test
    @DisplayName("类不存在时返回 null（调用方降级为空实现，应用照常启动）")
    void bridgeIsNullWhenToolkitMissing() {
        assertThat(ToolkitSpanBridge.tryCreate(
                getClass().getClassLoader(),
                "org.apache.skywalking.apm.toolkit.trace.NotExist",
                SkyWalkingToolkitStub.ActiveSpan.class.getName(),
                SkyWalkingToolkitStub.TraceContext.class.getName())).isNull();
    }

    @Test
    @DisplayName("span 生命周期：createLocalSpan → tag → stopSpan，close 幂等")
    void spanLifecycle() {
        SkyWalkingSpanBridge.SkyWalkingSpanHandle handle = bridge.startLocalSpan("agent.run");
        bridge.tagActiveSpan("aicode.trace_id", "trace-1");
        handle.close();
        handle.close();

        assertThat(SkyWalkingToolkitStub.CALLS).containsExactly(
                "createLocalSpan:agent.run",
                "tag:aicode.trace_id=trace-1",
                "stopSpan");
    }

    @Test
    @DisplayName("空操作名用兜底名（不产生空名 span），空标签被忽略")
    void blankNamesAreGuarded() {
        SkyWalkingSpanBridge.SkyWalkingSpanHandle handle = bridge.startLocalSpan(null);
        bridge.tagActiveSpan("", "v");
        bridge.tagActiveSpan("k", "");
        handle.close();

        assertThat(SkyWalkingToolkitStub.CALLS).containsExactly(
                "createLocalSpan:" + ToolkitSpanBridge.FALLBACK_OPERATION,
                "stopSpan");
    }

    @Test
    @DisplayName("错误标记与 traceId/spanId 读取")
    void errorAndContext() {
        SkyWalkingToolkitStub.spanId = 7;
        bridge.errorActiveSpan("IllegalStateException");

        assertThat(SkyWalkingToolkitStub.CALLS).containsExactly("error:IllegalStateException");
        assertThat(bridge.currentTraceId()).isEqualTo("sw-trace-1");
        assertThat(bridge.currentSpanId()).isEqualTo(7);
    }

    @Test
    @DisplayName("Toolkit 抛异常时桥不冒泡，且返回安全兜底句柄")
    void toolkitFailureDoesNotBubble() {
        SkyWalkingToolkitStub.failOnCreate = true;

        SkyWalkingSpanBridge.SkyWalkingSpanHandle handle = bridge.startLocalSpan("agent.run");
        assertThat(handle).isNotNull();
        handle.close();
        bridge.tagActiveSpan("k", "v");
        bridge.errorActiveSpan("boom");

        assertThat(SkyWalkingToolkitStub.CALLS).contains("createLocalSpan:agent.run");
    }

    @Test
    @DisplayName("traceId 缺失时返回空串（而不是 null）")
    void missingTraceIdBecomesEmptyString() {
        SkyWalkingToolkitStub.traceId = null;
        assertThat(bridge.currentTraceId()).isEmpty();
    }
}
