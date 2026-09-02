package com.aicode.demo.application;

import com.aicode.demo.domain.model.PromptDescriptor;
import com.aicode.demo.domain.port.PromptTemplatePort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 列出可用的 Prompt 模板。
 */
@Service
public class ListPromptsUseCase {

    private final PromptTemplatePort promptTemplatePort;

    public ListPromptsUseCase(PromptTemplatePort promptTemplatePort) {
        this.promptTemplatePort = promptTemplatePort;
    }

    /**
     * 列出当前版本所有模板名。
     */
    public List<PromptDescriptor> list() {
        return promptTemplatePort.list();
    }
}
