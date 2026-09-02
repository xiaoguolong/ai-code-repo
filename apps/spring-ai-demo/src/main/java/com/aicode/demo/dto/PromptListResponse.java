package com.aicode.demo.dto;

import com.aicode.demo.domain.model.PromptDescriptor;

import java.util.List;

/**
 * 模板列表响应。
 */
public record PromptListResponse(List<PromptItem> prompts) {

    /**
     * 从领域描述转换。
     */
    public static PromptListResponse from(List<PromptDescriptor> descriptors) {
        List<PromptItem> items = descriptors.stream()
                .map(d -> new PromptItem(d.name(), d.version()))
                .toList();
        return new PromptListResponse(items);
    }

    /**
     * 单个模板项。
     */
    public record PromptItem(String name, String version) {
    }
}
