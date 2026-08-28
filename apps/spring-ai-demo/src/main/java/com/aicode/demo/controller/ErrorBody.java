package com.aicode.demo.controller;

/**
 * 统一错误体。禁止包含 stack、SQL、厂商原始报文。
 */
public record ErrorBody(String code, String message) {
}
