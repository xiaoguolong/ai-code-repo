package com.aicode.framework.knowledge.domain;

import com.aicode.core.domain.exception.ChatModelException;
import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.exception.ToolExecutionException;
import com.aicode.core.domain.model.ChatMessage;
import com.aicode.core.domain.model.ChatOptions;
import com.aicode.core.domain.model.ChatResult;
import com.aicode.core.domain.model.MessageRole;
import com.aicode.core.domain.model.PromptTemplate;
import com.aicode.core.domain.model.TokenUsage;
import com.aicode.core.domain.model.ToolCall;
import com.aicode.core.domain.model.ToolResult;
import com.aicode.core.domain.model.VectorSearchHit;
import com.aicode.core.domain.RagContextAssembler;
import com.aicode.core.domain.port.ChatModelPort;
import com.aicode.core.domain.port.PromptTemplatePort;
import com.aicode.core.domain.port.ToolPort;
import com.aicode.framework.knowledge.application.KnowledgeRuntimeConfig;
import com.aicode.framework.knowledge.domain.model.KnowledgeAnswer;
import com.aicode.framework.knowledge.domain.model.KnowledgeCitation;
import com.aicode.framework.knowledge.domain.model.KnowledgeSearchHit;
import com.aicode.framework.knowledge.domain.model.KnowledgeStep;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库问答 Agent（Week 25，README 第 6 阶段 Agent 3）。
 *
 * <p>链路：调用 {@code KnowledgeSearchTool}（走 RBAC + Guardrail + 观测链）→ 取切片 →
 * {@link RagContextAssembler} 组装上下文 → 平台模型生成带引用的答案。</p>
 *
 * <p>三条安全约束：</p>
 * <ol>
 *   <li><b>只读检索、答案由平台生成</b>：不调用 enterprise 的 RAG 问答，避免绕过平台治理；</li>
 *   <li><b>无命中不瞎答</b>：检索为 0 条时直接返回「未检索到相关内容」，不进入模型调用；</li>
 *   <li><b>模型不可用降级</b>：返回原文摘录并标记 {@code degraded=true}，不编造答案。</li>
 * </ol>
 */
public class KnowledgeQaAgent {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeQaAgent.class);

    /** 检索工具名（与 {@code KnowledgeSearchTool} 的定义一致）。 */
    public static final String TOOL_NAME = "KnowledgeSearchTool";

    private static final String PROMPT_NAME = "knowledge-qa";

    /** 降级摘录单条正文的最大长度。 */
    static final int DEGRADED_EXCERPT_CHARS = 200;

    private final ToolPort toolPort;
    private final ChatModelPort chatModelPort;
    private final PromptTemplatePort promptTemplatePort;
    private final ObjectMapper objectMapper;
    private final KnowledgeRuntimeConfig config;

    public KnowledgeQaAgent(
            ToolPort toolPort,
            ChatModelPort chatModelPort,
            PromptTemplatePort promptTemplatePort,
            ObjectMapper objectMapper,
            KnowledgeRuntimeConfig config
    ) {
        this.toolPort = toolPort;
        this.chatModelPort = chatModelPort;
        this.promptTemplatePort = promptTemplatePort;
        this.objectMapper = objectMapper;
        this.config = config;
    }

    /**
     * 回答知识库问题。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param question        问题（调用方已校验非空）
     * @return 带引用的答案
     * @throws ToolExecutionException 检索失败（上游不可用/未绑定/越权）
     */
    public KnowledgeAnswer run(long knowledgeBaseId, String question) {
        List<KnowledgeStep> steps = new ArrayList<>();
        ToolResult toolResult = toolPort.execute(new ToolCall(
                "search_knowledge", TOOL_NAME, toJson(knowledgeBaseId, question)));
        Retrieved retrieved = parse(toolResult.output());
        addStep(steps, "knowledge_search",
                "hits=" + retrieved.hits().size() + " kb=" + retrieved.knowledgeBaseName());

        if (retrieved.hits().isEmpty()) {
            addStep(steps, "answer_generation", "skipped: no hits");
            log.info("[knowledge-qa] no hits knowledgeBaseId={}", knowledgeBaseId);
            return new KnowledgeAnswer(
                    knowledgeBaseId, retrieved.knowledgeBaseName(), question,
                    "知识库中没有检索到与问题相关的内容，请换一种问法或先补充文档。",
                    List.of(), 0, false, TokenUsage.unknown(), "", steps);
        }

        List<KnowledgeCitation> citations = retrieved.hits().stream()
                .map(hit -> new KnowledgeCitation(
                        hit.documentId(), hit.documentName(), hit.chunkIndex(), hit.content(), hit.score()))
                .toList();
        AnswerOutcome outcome = generateAnswer(question, retrieved.hits());
        addStep(steps, "answer_generation", "degraded=" + outcome.degraded());
        log.info("[knowledge-qa] answered knowledgeBaseId={} hits={} degraded={} tokens={}",
                knowledgeBaseId, retrieved.hits().size(), outcome.degraded(), outcome.usage().totalTokens());

        return new KnowledgeAnswer(
                knowledgeBaseId, retrieved.knowledgeBaseName(), question, outcome.answer(),
                citations, retrieved.hits().size(), outcome.degraded(),
                outcome.usage(), outcome.model(), steps);
    }

    private AnswerOutcome generateAnswer(String question, List<KnowledgeSearchHit> hits) {
        String fallback = composeExcerptAnswer(hits);
        try {
            PromptTemplate system = promptTemplatePort.load(PROMPT_NAME);
            List<VectorSearchHit> context = hits.stream()
                    .map(hit -> new VectorSearchHit(
                            hit.documentId(), hit.knowledgeBaseId(), hit.chunkIndex(), hit.content(), hit.score()))
                    .toList();
            ChatResult result = chatModelPort.chat(
                    List.of(
                            new ChatMessage(MessageRole.SYSTEM, system.content()),
                            new ChatMessage(MessageRole.USER,
                                    RagContextAssembler.buildUserMessage(context, question))),
                    new ChatOptions(config.model(), config.temperature(), config.maxTokens()));
            String content = result.content() == null ? "" : result.content().trim();
            if (content.isEmpty()) {
                log.warn("[knowledge-qa] empty model content, degrade to excerpts");
                return new AnswerOutcome(fallback, true, TokenUsage.unknown(), config.model());
            }
            String model = (result.model() == null || result.model().isBlank()) ? config.model() : result.model();
            return new AnswerOutcome(content, false, result.usage(), model);
        } catch (ChatModelException | InvalidChatRequestException ex) {
            log.warn("[knowledge-qa] model/prompt unavailable, degrade to excerpts error={}", ex.getMessage());
            return new AnswerOutcome(fallback, true, TokenUsage.unknown(), config.model());
        }
    }

    private String composeExcerptAnswer(List<KnowledgeSearchHit> hits) {
        StringBuilder builder = new StringBuilder("## 知识库检索结果\n");
        builder.append("> 模型暂不可用，以下为原文摘录（未做归纳）；请以原文为准。\n\n");
        int index = 1;
        for (KnowledgeSearchHit hit : hits) {
            String excerpt = hit.content().length() > DEGRADED_EXCERPT_CHARS
                    ? hit.content().substring(0, DEGRADED_EXCERPT_CHARS) + "…"
                    : hit.content();
            builder.append(index++).append(". 《").append(hit.documentName().isBlank()
                            ? hit.documentId() : hit.documentName())
                    .append("》第 ").append(hit.chunkIndex()).append(" 段：")
                    .append(excerpt).append('\n');
        }
        return builder.toString();
    }

    private Retrieved parse(String output) {
        try {
            JsonNode root = objectMapper.readTree(output == null ? "{}" : output);
            String knowledgeBaseName = root.path("knowledgeBaseName").asText("");
            List<KnowledgeSearchHit> hits = new ArrayList<>();
            for (JsonNode item : root.path("hits")) {
                hits.add(new KnowledgeSearchHit(
                        item.path("knowledgeBaseId").asLong(),
                        item.path("documentId").asText(""),
                        item.path("documentName").asText(""),
                        item.path("chunkIndex").asInt(0),
                        item.path("content").asText(""),
                        item.path("score").asDouble(0.0)));
            }
            return new Retrieved(knowledgeBaseName, hits);
        } catch (Exception ex) {
            throw new ToolExecutionException("invalid knowledge tool result", ex);
        }
    }

    private String toJson(long knowledgeBaseId, String question) {
        try {
            return objectMapper.writeValueAsString(
                    java.util.Map.of("knowledgeBaseId", knowledgeBaseId, "query", question));
        } catch (Exception ex) {
            throw new ToolExecutionException("failed to serialize knowledge tool arguments", ex);
        }
    }

    private void addStep(List<KnowledgeStep> steps, String step, String summary) {
        steps.add(new KnowledgeStep(steps.size() + 1, step, summary));
    }

    /**
     * 检索结果（内部值对象）。
     *
     * @param knowledgeBaseName 知识库名称
     * @param hits              命中切片
     */
    private record Retrieved(String knowledgeBaseName, List<KnowledgeSearchHit> hits) {
    }

    /**
     * 答案生成结果（内部值对象）。
     *
     * @param answer   正文
     * @param degraded 是否降级
     * @param usage    用量
     * @param model    模型名
     */
    private record AnswerOutcome(String answer, boolean degraded, TokenUsage usage, String model) {
    }
}
