package com.aicode.framework.observability.infrastructure;

import com.aicode.framework.observability.domain.TraceDimensions;
import com.aicode.framework.observability.domain.TraceScope;

import java.util.Optional;

/**
 * 请求期 Langfuse 维度持有者（Week 18，单例 + ThreadLocal）。
 *
 * <p>为什么需要它：trace 维度（用户、会话、trace 名、标签）在请求处理过程中才可知，
 * 而 {@code LangfuseSpanEnricher} 是在每个 span <b>开始</b>时读取这些值 —— 两者之间需要一个
 * 请求期载体。用 ThreadLocal 是因为 OTel 的 {@code SpanProcessor.onStart} 与创建 span 的
 * 业务线程是同一个线程（同步回调），不存在跨线程可见性问题。</p>
 *
 * <p>生命周期由 {@link TraceScope} 保证：进入写入、退出恢复（含嵌套场景），
 * 避免 Tomcat 线程复用时把上一个请求的维度带到下一个请求。</p>
 */
public class LangfuseContext {

    private final ThreadLocal<TraceDimensions> current = new ThreadLocal<>();

    /**
     * 进入一个维度作用域。
     *
     * @param dimensions 维度，可为 null（等价于只做「记住并恢复」）
     * @return 作用域句柄，close 时恢复到进入前的值
     */
    public TraceScope begin(TraceDimensions dimensions) {
        TraceDimensions previous = current.get();
        if (dimensions == null) {
            current.remove();
        } else {
            current.set(dimensions);
        }
        return new RestoringScope(previous);
    }

    /**
     * 当前维度。
     *
     * @return 有维度时返回值，否则空
     */
    public Optional<TraceDimensions> current() {
        return Optional.ofNullable(current.get());
    }

    /** 清理当前线程（供过滤器收尾使用，防御性兜底）。 */
    public void clear() {
        current.remove();
    }

    /** 退出作用域时恢复进入前的维度；close 幂等。 */
    private final class RestoringScope implements TraceScope {

        private final TraceDimensions previous;
        private boolean closed;

        private RestoringScope(TraceDimensions previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                current.remove();
            } else {
                current.set(previous);
            }
        }
    }
}
