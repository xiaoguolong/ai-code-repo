package com.aicode.core.domain.port;

import com.aicode.core.domain.exception.ChatModelException;
import com.aicode.core.domain.exception.StreamCancelledException;
import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatOptions;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.ChatStreamListener;
import com.aicode.core.domain.model.ToolDefinition;

import java.util.List;

/**
 * 出站端口：调用大模型。实现类负责厂商协议，领域不依赖 SDK。
 */
public interface ChatModelPort {

    /**
     * 发起一次聊天补全（不带工具）。
     */
    default ChatResult chat(List<ChatMessage> messages, ChatOptions options) {
        return chat(messages, options, List.of());
    }

    /**
     * 发起一次聊天补全，可携带工具定义（原生 Function Calling）。
     *
     * @param messages 已按 system/user/assistant/tool 分离的消息列表
     * @param options  模型名与采样参数
     * @param tools    可用工具定义，空列表表示不启用工具
     * @return 助手内容、工具调用、结束原因与 Token 用量
     * @throws ChatModelException 网络、协议或密钥问题
     */
    ChatResult chat(List<ChatMessage> messages, ChatOptions options, List<ToolDefinition> tools);

    /**
     * 发起一次增量聊天补全（Week 31）。
     *
     * <p>语义：每段增量回调 {@link ChatStreamListener#onDelta(String)}，方法本身返回
     * <b>累加后的完整结果</b>（与同步 {@link #chat} 同结构），调用方不需要自己拼。</p>
     *
     * <p>默认实现退化为同步调用：整段正文作为<b>一个</b>增量回调后返回。
     * 所有既有适配器（含测试桩）无需改动即可继续工作，只是没有首字收益；
     * 支持真增量的适配器应覆盖本方法。</p>
     *
     * @param messages 已按 system/user/assistant/tool 分离的消息列表
     * @param options  模型名与采样参数
     * @param listener 增量回调；被取消时实现方应尽快停止上游生成
     * @return 累加后的完整结果
     * @throws ChatModelException     网络、协议或密钥问题
     * @throws StreamCancelledException 调用方在完成前取消
     */
    default ChatResult chatStream(
            List<ChatMessage> messages,
            ChatOptions options,
            ChatStreamListener listener
    ) {
        if (listener.isCancelled()) {
            throw new StreamCancelledException("chat stream cancelled before start");
        }
        ChatResult result = chat(messages, options);
        if (result.content() != null && !result.content().isBlank()) {
            listener.onDelta(result.content());
        }
        if (listener.isCancelled()) {
            throw new StreamCancelledException("chat stream cancelled before completion");
        }
        return result;
    }
}
