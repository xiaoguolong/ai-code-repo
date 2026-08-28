package com.aicode.demo.domain.port;

import com.aicode.demo.domain.model.PromptTemplate;

/**
 * 出站端口：加载带版本的系统提示，禁止在业务代码里硬编码 Prompt。
 */
public interface PromptTemplatePort {

    /**
     * 加载当前配置版本的系统提示。
     *
     * @return 版本号与文本
     */
    PromptTemplate loadSystemPrompt();
}
