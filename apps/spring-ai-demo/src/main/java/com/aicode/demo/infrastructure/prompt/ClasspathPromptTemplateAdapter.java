package com.aicode.demo.infrastructure.prompt;

import com.aicode.demo.domain.model.PromptTemplate;
import com.aicode.demo.domain.port.PromptTemplatePort;
import com.aicode.demo.infrastructure.config.ChatAppProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 从 classpath prompts/system-{version}.txt 加载系统提示。版本只允许字母数字和点横线。
 */
@Component
public class ClasspathPromptTemplateAdapter implements PromptTemplatePort {

    private final ChatAppProperties properties;

    public ClasspathPromptTemplateAdapter(ChatAppProperties properties) {
        this.properties = properties;
    }

    /**
     * @return 当前配置版本的系统提示
     * @throws IllegalStateException 文件缺失或版本非法
     */
    @Override
    public PromptTemplate loadSystemPrompt() {
        String version = properties.systemPromptVersion();
        // 防止路径穿越：版本号不允许出现 / 或 ..
        if (version == null || !version.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalStateException("invalid prompt version");
        }
        String path = "prompts/system-" + version + ".txt";
        try {
            ClassPathResource resource = new ClassPathResource(path);
            String content = resource.getContentAsString(StandardCharsets.UTF_8).trim();
            return new PromptTemplate(version, content);
        } catch (Exception ex) {
            throw new IllegalStateException("prompt template not found: " + path, ex);
        }
    }
}
