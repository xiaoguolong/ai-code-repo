package com.aicode.framework.observability.infrastructure;

import com.aicode.framework.observability.domain.PromptReference;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Prompt 版本关联记录（Week 18，单例 + ThreadLocal）。
 *
 * <p>问题：一次请求里可能先后加载多个 Prompt（例如「报告」与「随访」），
 * 而 {@code llm.chat} 只知道消息内容、不知道用的是哪个 Prompt。</p>
 *
 * <p>做法：Prompt 适配器每次从 Langfuse 取到 Prompt 就把 {@code (name, version, 渲染后内容)}
 * 记到当前线程；模型调用时用 <b>system 消息内容全等</b>匹配。匹配不上就不关联 ——
 * 宁可不挂版本，也不产生错误归因（错误归因会让 Prompt 迭代对比得出反向结论）。</p>
 *
 * <p>容量上限 16 条：一个请求加载的 Prompt 数量远小于此，超出即淘汰最早的条目，避免线程泄漏。</p>
 */
public class LangfusePromptTracker {

    /** 单线程最多保留的 Prompt 条目数。 */
    static final int MAX_ENTRIES = 16;

    private final ThreadLocal<Deque<LoadedPrompt>> entries = ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * 记录一次加载。
     *
     * @param name    Prompt 名，空则忽略
     * @param version Langfuse 版本号，空则忽略
     * @param content 渲染后的内容（与调用方实际使用的 system 消息一致），空则忽略
     */
    public void recordLoaded(String name, String version, String content) {
        if (isBlank(name) || isBlank(version) || isBlank(content)) {
            return;
        }
        Deque<LoadedPrompt> deque = entries.get();
        deque.addLast(new LoadedPrompt(name, version, content));
        while (deque.size() > MAX_ENTRIES) {
            deque.removeFirst();
        }
    }

    /**
     * 按内容全等匹配已加载的 Prompt。
     *
     * @param content 当前请求的 system 消息内容，可为 null
     * @return 命中时返回 Prompt 引用；未命中或内容为空时返回 null
     */
    public PromptReference match(String content) {
        if (isBlank(content)) {
            return null;
        }
        Deque<LoadedPrompt> deque = entries.get();
        // 从最近一次开始找：同一 Prompt 多次加载时以最后一次为准
        var iterator = deque.descendingIterator();
        while (iterator.hasNext()) {
            LoadedPrompt loaded = iterator.next();
            if (content.equals(loaded.content())) {
                return new PromptReference(loaded.name(), loaded.version());
            }
        }
        return null;
    }

    /** 清理当前线程记录（请求结束或测试收尾）。 */
    public void clear() {
        entries.remove();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 一条已加载的 Prompt。 */
    private record LoadedPrompt(String name, String version, String content) {
    }
}
