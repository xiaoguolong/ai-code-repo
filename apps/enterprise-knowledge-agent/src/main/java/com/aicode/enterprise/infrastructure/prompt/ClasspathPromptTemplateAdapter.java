package com.aicode.enterprise.infrastructure.prompt;

import com.aicode.enterprise.domain.exception.InvalidChatRequestException;
import com.aicode.enterprise.domain.model.PromptDescriptor;
import com.aicode.enterprise.domain.model.PromptTemplate;
import com.aicode.enterprise.domain.port.PromptTemplatePort;
import com.aicode.enterprise.infrastructure.config.ChatAppProperties;
import dev.langchain4j.model.input.Prompt;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 从 classpath prompts/{name}-{version}.txt 加载系统提示。版本只允许字母数字和点横线。
 */
@Component
public class ClasspathPromptTemplateAdapter implements PromptTemplatePort {

    /** 模板名白名单：只允许字母、数字、点、横线。 */
    private static final String NAME_PATTERN = "[A-Za-z0-9._-]+";

    private final ChatAppProperties properties;
    private final PathMatchingResourcePatternResolver resolver;

    public ClasspathPromptTemplateAdapter(ChatAppProperties properties) {
        this.properties = properties;
        this.resolver = new PathMatchingResourcePatternResolver();
    }

    @Override
    public PromptTemplate loadSystemPrompt() {
        return load("system");
    }

    @Override
    public PromptTemplate load(String name) {
        return render(name, Map.of());
    }

    @Override
    public PromptTemplate render(String name, Map<String, Object> variables) {
        validateName(name);
        String version = properties.systemPromptVersion();
        validateVersion(version);
        String path = "prompts/" + name + "-" + version + ".txt";
        try {
            ClassPathResource resource = new ClassPathResource(path);
            String raw = resource.getContentAsString(StandardCharsets.UTF_8).trim();
            dev.langchain4j.model.input.PromptTemplate template =
                    dev.langchain4j.model.input.PromptTemplate.from(raw);
            Prompt prompt = template.apply(variables);
            return new PromptTemplate(version, prompt.text());
        } catch (InvalidChatRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InvalidChatRequestException("prompt template not found or invalid: " + name);
        }
    }

    @Override
    public List<PromptDescriptor> list() {
        String version = properties.systemPromptVersion();
        try {
            return Arrays.stream(resolver.getResources("classpath:prompts/*-" + version + ".txt"))
                    .map(resource -> resource.getFilename())
                    .map(name -> name.substring(0, name.lastIndexOf("-" + version)))
                    .filter(this::isValidName)
                    .sorted()
                    .map(name -> new PromptDescriptor(name, version))
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("failed to list prompt templates", ex);
        }
    }

    private void validateName(String name) {
        if (name == null || !name.matches(NAME_PATTERN)) {
            throw new InvalidChatRequestException("invalid prompt template name");
        }
    }

    private boolean isValidName(String name) {
        return name != null && name.matches(NAME_PATTERN);
    }

    private void validateVersion(String version) {
        if (version == null || !version.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalStateException("invalid prompt version");
        }
    }
}
