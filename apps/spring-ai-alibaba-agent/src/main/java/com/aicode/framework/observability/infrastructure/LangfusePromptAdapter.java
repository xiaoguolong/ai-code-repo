package com.aicode.framework.observability.infrastructure;

import com.aicode.core.domain.model.PromptDescriptor;
import com.aicode.core.domain.model.PromptTemplate;
import com.aicode.core.domain.port.PromptTemplatePort;
import com.aicode.framework.observability.domain.AgentObservabilityPort;
import com.aicode.framework.observability.domain.ObservabilityAttributes;

import dev.langchain4j.model.input.Prompt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Langfuse Prompt 管理适配器（Week 18）：装饰 classpath 模板端口。
 *
 * <p>策略「Langfuse 优先、本地兜底」：</p>
 * <ol>
 *   <li>命中 Langfuse（{@code prompt.enabled=true} 且拉取成功）→ 用它的文本与版本，变量仍由本地模板引擎渲染；</li>
 *   <li>TTL 内走客户端缓存（默认 60s，与官方 SDK 默认一致），避免每次请求都打 Prompt API；</li>
 *   <li>404 / 5xx / 网络异常 → <b>回退 classpath 模板</b>并记 WARN + 指标，业务不感知。</li>
 * </ol>
 *
 * <p>它不改变 {@link PromptTemplatePort} 契约（规范 5.8.2：ai-core 契约稳定），
 * 因此 Langfuse 关闭时行为与 Week 8–17 完全一致。</p>
 */
public class LangfusePromptAdapter implements PromptTemplatePort {

    private static final Logger log = LoggerFactory.getLogger(LangfusePromptAdapter.class);

    /** Prompt 拉取结果计数指标（Prometheus 出口 {@code langfuse_prompt_fetch_count_total}）。 */
    static final String METRIC_FETCH_COUNT = "langfuse.prompt.fetch.count";

    private static final String TAG_OUTCOME = "outcome";

    private final PromptTemplatePort delegate;
    private final LangfusePromptClient client;
    private final LangfusePromptTracker tracker;
    private final LangfuseProperties properties;
    private final AgentObservabilityPort observability;
    private final Map<String, CachedPrompt> cache = new ConcurrentHashMap<>();

    /**
     * @param delegate      本地（classpath）模板端口，回退用
     * @param client        Langfuse Prompt API 客户端
     * @param tracker       Prompt 版本关联记录
     * @param properties    Langfuse 配置（标签 / 缓存 TTL）
     * @param observability 观测端口，用于记拉取结果指标
     */
    public LangfusePromptAdapter(
            PromptTemplatePort delegate,
            LangfusePromptClient client,
            LangfusePromptTracker tracker,
            LangfuseProperties properties,
            AgentObservabilityPort observability
    ) {
        this.delegate = delegate;
        this.client = client;
        this.tracker = tracker;
        this.properties = properties;
        this.observability = observability;
    }

    @Override
    public PromptTemplate load(String name) {
        return render(name, Map.of());
    }

    /**
     * 渲染模板：Langfuse 命中则用其文本与版本，否则完全交给本地模板端口。
     *
     * @param name      模板名
     * @param variables 变量
     * @return 带版本的模板；来源可能是 Langfuse 或 classpath
     */
    @Override
    public PromptTemplate render(String name, Map<String, Object> variables) {
        LangfusePromptPayload payload = resolve(name);
        if (payload == null) {
            return delegate.render(name, variables);
        }
        String content = renderText(payload.text(), variables);
        if (tracker != null) {
            tracker.recordLoaded(name, payload.version(), content);
        }
        return new PromptTemplate(payload.version(), content);
    }

    @Override
    public List<PromptDescriptor> list() {
        // 列表仍以本地模板为准：Langfuse 只负责「同名模板的内容与版本」
        return delegate.list();
    }

    /** 取 Prompt：先看缓存，再拉 Langfuse，并按结果记指标。 */
    private LangfusePromptPayload resolve(String name) {
        long ttlMillis = properties.resolvedPrompt().resolvedCacheTtlSeconds() * 1000L;
        CachedPrompt cached = name == null ? null : cache.get(name);
        if (cached != null && ttlMillis > 0 && System.currentTimeMillis() - cached.fetchedAt() < ttlMillis) {
            return cached.payload();
        }
        LangfusePromptFetch fetch = client.fetch(name);
        recordOutcome(fetch.outcome());
        if (!fetch.present()) {
            return null;
        }
        LangfusePromptPayload payload = fetch.payload().get();
        if (name != null && !name.isBlank()) {
            cache.put(name, new CachedPrompt(payload, System.currentTimeMillis()));
        }
        return payload;
    }

    /** 用与 classpath 相同的模板引擎渲染，保证两种来源的变量语义一致。 */
    private String renderText(String text, Map<String, Object> variables) {
        Map<String, Object> safeVariables = variables == null ? Map.of() : variables;
        Prompt prompt = dev.langchain4j.model.input.PromptTemplate.from(text).apply(safeVariables);
        return prompt.text();
    }

    /** 记一次拉取结果（hit / miss / fallback），排障时能分清「没配」与「连不上」。 */
    private void recordOutcome(String outcome) {
        if (observability == null) {
            return;
        }
        observability.recordCounter(METRIC_FETCH_COUNT, 1.0, TAG_OUTCOME, outcome);
    }

    /** 缓存条目（不可变）。 */
    private record CachedPrompt(LangfusePromptPayload payload, long fetchedAt) {
    }
}
