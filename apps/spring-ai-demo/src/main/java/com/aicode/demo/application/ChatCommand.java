package com.aicode.demo.application;

/**
 * 发起聊天的用例入参。sessionId 可空，由用例生成。
 */
public record ChatCommand(String sessionId, String message) {
}
