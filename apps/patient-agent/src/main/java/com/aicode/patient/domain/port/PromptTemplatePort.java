package com.aicode.patient.domain.port;

import com.aicode.patient.domain.model.PromptDescriptor;
import com.aicode.patient.domain.model.PromptTemplate;

import java.util.List;
import java.util.Map;

/**
 * 出站端口：加载带版本的系统提示，禁止在业务代码里硬编码 Prompt。
 */
public interface PromptTemplatePort {

    /**
     * 按模板名加载当前配置版本。
     */
    PromptTemplate load(String name);

    /**
     * 列出 classpath 上可用的模板名与版本。
     */
    List<PromptDescriptor> list();

    /**
     * 按模板名渲染变量。空变量时等价于 load。
     */
    PromptTemplate render(String name, Map<String, Object> variables);
}
