package com.aicode.demo.domain.port;

import com.aicode.demo.domain.exception.ChatModelException;
import com.aicode.demo.domain.model.ChatMessage;
import com.aicode.demo.domain.model.ChatOptions;
import com.aicode.demo.domain.model.ChatResult;

import java.util.List;

/**
 * 出站端口：调用大模型。实现类负责厂商协议，领域不依赖 SDK。
 */
public interface ChatModelPort {

    /**
     * 发起一次聊天补全。
     *
     * @param messages 已按 system/user 分离的消息列表
     * @param options  模型名与采样参数
     * @return 助手内容与 Token 用量
     * @throws ChatModelException 网络、协议或密钥问题
     */
    ChatResult chat(List<ChatMessage> messages, ChatOptions options);
}
