package com.aicode.core.domain.port;

import com.aicode.core.domain.exception.ChatModelException;
import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatOptions;
import com.aicode.core.domain.model.ChatResult;
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
}
