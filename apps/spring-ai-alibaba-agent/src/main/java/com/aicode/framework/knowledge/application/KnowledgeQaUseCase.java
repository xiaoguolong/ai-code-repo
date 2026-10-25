package com.aicode.framework.knowledge.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.framework.knowledge.domain.KnowledgeQaAgent;
import com.aicode.framework.knowledge.domain.model.KnowledgeAnswer;
import com.aicode.framework.platform.domain.exception.PlatformNotFoundException;
import com.aicode.framework.platform.domain.model.KnowledgeBinding;
import com.aicode.framework.platform.domain.model.PlatformMemoryScope;
import com.aicode.framework.platform.domain.port.KnowledgeBindingPort;
import com.aicode.framework.platform.domain.port.PlatformRunScopePort;
import com.aicode.framework.platform.infrastructure.config.PlatformKnowledgeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 知识库问答用例（Week 25，Agent 3）：校验租户绑定 → 交给 Agent 检索并生成答案。
 *
 * <p>绑定校验放在用例层而不是只靠工具：未绑定的知识库应当在**调用模型之前**就 404，
 * 而不是先烧一次 token 再在工具里报错。工具里的同名校验是第二道防线
 * （防止有人绕过用例直接调工具）。</p>
 */
@Service
public class KnowledgeQaUseCase {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeQaUseCase.class);

    private final KnowledgeBindingPort bindingPort;
    private final PlatformRunScopePort runScopePort;
    private final KnowledgeQaAgent agent;
    private final PlatformKnowledgeProperties properties;

    public KnowledgeQaUseCase(
            KnowledgeBindingPort bindingPort,
            PlatformRunScopePort runScopePort,
            KnowledgeQaAgent agent,
            PlatformKnowledgeProperties properties
    ) {
        this.bindingPort = bindingPort;
        this.runScopePort = runScopePort;
        this.agent = agent;
        this.properties = properties == null ? new PlatformKnowledgeProperties() : properties;
    }

    /**
     * 回答知识库问题。
     *
     * @param callerUserId    调用者（来自登录态，仅用于日志）
     * @param knowledgeBaseId 知识库 ID
     * @param question        问题
     * @return 带引用的答案
     * @throws InvalidChatRequestException 入参非法
     * @throws PlatformNotFoundException   知识库未绑定到当前租户
     */
    public KnowledgeAnswer run(long callerUserId, Long knowledgeBaseId, String question) {
        PlatformMemoryScope scope = runScopePort.currentScope()
                .orElseThrow(() -> new IllegalStateException("knowledge qa requires platform run scope"));
        if (knowledgeBaseId == null || knowledgeBaseId <= 0) {
            throw new InvalidChatRequestException("knowledgeBaseId must be positive");
        }
        String normalizedQuestion = question == null ? "" : question.trim();
        if (normalizedQuestion.isEmpty()) {
            throw new InvalidChatRequestException("question must not be blank");
        }
        if (normalizedQuestion.length() > properties.getMaxQuestionChars()) {
            throw new InvalidChatRequestException(
                    "question must not exceed " + properties.getMaxQuestionChars() + " characters");
        }

        KnowledgeBinding binding = bindingPort
                .findByTenantAndKnowledgeBase(scope.tenantKey(), knowledgeBaseId)
                .orElseThrow(() -> new PlatformNotFoundException(
                        "knowledge base is not bound: " + knowledgeBaseId));
        if (!binding.enabled()) {
            throw new PlatformNotFoundException(
                    "knowledge base is disabled: " + knowledgeBaseId);
        }
        if (binding.missing()) {
            // 对账已确认企业侧删除：在调模型之前 404，不伪装成「没有检索到」。
            throw new PlatformNotFoundException(
                    "knowledge base was removed on enterprise side: " + knowledgeBaseId);
        }
        log.info("[knowledge-qa] run callerUserId={} tenantKey={} knowledgeBaseId={}",
                callerUserId, scope.tenantKey(), knowledgeBaseId);
        return agent.run(knowledgeBaseId, normalizedQuestion);
    }
}
