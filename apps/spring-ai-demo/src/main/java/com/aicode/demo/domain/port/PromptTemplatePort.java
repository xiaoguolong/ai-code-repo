package com.aicode.demo.domain.port;

import com.aicode.demo.domain.model.PromptDescriptor;
import com.aicode.demo.domain.model.PromptTemplate;

import java.util.List;
import java.util.Map;

/**
 * 出站端口：加载带版本的系统提示，禁止在业务代码里硬编码 Prompt。
 */
public interface PromptTemplatePort {

    /**
     * 加载当前配置版本的默认系统提示（模板名 system）。
     *
     * @return 版本号与文本
     */
    PromptTemplate loadSystemPrompt();

    /**
     * 按模板名加载当前配置版本。
     *
     * @param name 模板名，只允许字母数字点横线
     * @return 版本号与渲染后的文本
     */
    PromptTemplate load(String name);

    /**
     * 列出 classpath 上可用的模板名与版本。
     */
    List<PromptDescriptor> list();

    /**
     * 按模板名渲染变量。空变量时等价于 load。
     *
     * @param name      模板名
     * @param variables 模板变量
     * @return 渲染后的模板
     */
    PromptTemplate render(String name, Map<String, Object> variables);
}
