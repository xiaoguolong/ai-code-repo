package com.aicode.framework.platform.domain.port;

import com.aicode.framework.platform.domain.model.PlatformMemoryScope;

import java.util.Optional;

/**
 * 当前 Run 的访问范围端口（Week 21）。
 *
 * <p>患者分析 Agent 需要「谁在跑」才能安全写记忆，但编排层不应直接依赖 Web 上下文。
 * 该端口由基础设施适配器实现（从 {@code PlatformSecurityContext} + 用户记录推导），
 * Agent 只读得到结果、拿不到请求对象。</p>
 */
public interface PlatformRunScopePort {

    /**
     * @return 当前 Run 的租户+用户范围；不在平台调度上下文（如直连 Run、单测）时为空
     */
    Optional<PlatformMemoryScope> currentScope();

    /** 空实现：供旧构造器/单测兜底，任何情况下都返回空。 */
    PlatformRunScopePort NONE = Optional::empty;
}
