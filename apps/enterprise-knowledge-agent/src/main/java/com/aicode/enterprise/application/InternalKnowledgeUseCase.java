package com.aicode.enterprise.application;

import com.aicode.core.domain.exception.InvalidChatRequestException;
import com.aicode.core.domain.exception.NotFoundException;
import com.aicode.core.domain.model.VectorSearchHit;
import com.aicode.core.domain.port.EmbeddingModelPort;
import com.aicode.core.domain.port.VectorStorePort;
import com.aicode.enterprise.domain.model.DocumentMetadata;
import com.aicode.enterprise.domain.model.KnowledgeBase;
import com.aicode.enterprise.domain.port.DocumentPort;
import com.aicode.enterprise.domain.port.KnowledgeBasePort;
import com.aicode.enterprise.domain.port.SsoUserLinkPort;
import com.aicode.enterprise.domain.port.UserPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内部知识库能力（Week 25）：给平台侧「包装」用的**只读**检索与清单。
 *
 * <p>与 {@link AskKnowledgeUseCase} 的区别：本用例**不调用模型、不写会话、不记 Token**——
 * 平台侧要自己生成答案，才能进平台的 Guardrail / 审计 / 执行记录 / 可观测。
 * 这里只做「归属校验 + 向量化 + 检索 + 文档名回填」。</p>
 *
 * <p>归属语义：检索按 {@code enterpriseUserId} 校验知识库归属（平台绑定时已记录该本地用户），
 * 不再依赖平台用户 ↔ 本地用户的实时映射；清单接口需要实时映射，因为绑定时要用平台用户身份
 * 查「他名下的知识库」。</p>
 */
@Service
public class InternalKnowledgeUseCase {

    private static final Logger log = LoggerFactory.getLogger(InternalKnowledgeUseCase.class);

    /** 单次检索条数硬上限（与对外 RAG 接口一致）。 */
    static final int MAX_TOP_K = 50;

    private final KnowledgeBasePort knowledgeBasePort;
    private final DocumentPort documentPort;
    private final SsoUserLinkPort ssoUserLinkPort;
    private final UserPort userPort;
    private final EmbeddingModelPort embeddingModelPort;
    private final VectorStorePort vectorStorePort;
    private final RagRuntimeConfig ragConfig;

    public InternalKnowledgeUseCase(
            KnowledgeBasePort knowledgeBasePort,
            DocumentPort documentPort,
            SsoUserLinkPort ssoUserLinkPort,
            UserPort userPort,
            EmbeddingModelPort embeddingModelPort,
            VectorStorePort vectorStorePort,
            RagRuntimeConfig ragConfig
    ) {
        this.knowledgeBasePort = knowledgeBasePort;
        this.documentPort = documentPort;
        this.ssoUserLinkPort = ssoUserLinkPort;
        this.userPort = userPort;
        this.embeddingModelPort = embeddingModelPort;
        this.vectorStorePort = vectorStorePort;
        this.ragConfig = ragConfig;
    }

    /**
     * 列出某平台用户映射到的本地用户的知识库。
     *
     * @param platformUserId 平台用户 ID
     * @return 本地用户 ID + 知识库清单
     * @throws NotFoundException 该平台用户未建立 SSO 映射
     */
    public InternalKnowledgeCatalog listBases(long platformUserId) {
        long enterpriseUserId = ssoUserLinkPort.findEnterpriseUserId(platformUserId)
                .orElseThrow(() -> new NotFoundException(
                        "platform user is not linked to an enterprise user: " + platformUserId));
        return catalogOf(enterpriseUserId, "platformUserId=" + platformUserId);
    }

    /**
     * 按企业本地用户列出知识库（Week 27：平台对账用）。
     *
     * <p>与 {@link #listBases(long)} 的区别：不依赖平台用户 ↔ 企业用户的实时 SSO 映射，
     * 因为绑定表里记录的就是 {@code enterpriseUserId}，导入人离职/解绑不应让对账失效。</p>
     *
     * @param enterpriseUserId 企业本地用户 ID
     * @return 本地用户 ID + 知识库清单
     * @throws InvalidChatRequestException ID 非正数
     * @throws NotFoundException           本地用户不存在（平台按整组失败处理，不标记删除）
     */
    public InternalKnowledgeCatalog listBasesByEnterpriseUser(long enterpriseUserId) {
        if (enterpriseUserId <= 0) {
            throw new InvalidChatRequestException("enterpriseUserId must be positive");
        }
        userPort.findById(enterpriseUserId)
                .orElseThrow(() -> new NotFoundException(
                        "enterprise user not found: " + enterpriseUserId));
        return catalogOf(enterpriseUserId, "enterpriseUserId=" + enterpriseUserId);
    }

    private InternalKnowledgeCatalog catalogOf(long enterpriseUserId, String source) {
        List<InternalKnowledgeBaseView> bases = knowledgeBasePort.listByUser(enterpriseUserId).stream()
                .map(this::toView)
                .toList();
        log.info("[enterprise-internal] list bases {} enterpriseUserId={} count={}",
                source, enterpriseUserId, bases.size());
        return new InternalKnowledgeCatalog(enterpriseUserId, bases);
    }

    /**
     * 检索知识库切片（不生成答案）。
     *
     * @param command 检索命令
     * @return 知识库信息 + 命中切片
     * @throws InvalidChatRequestException 问题为空
     * @throws NotFoundException           知识库不存在或不属于该本地用户
     */
    public InternalKnowledgeResult search(InternalKnowledgeSearchCommand command) {
        String query = command.query() == null ? "" : command.query().trim();
        if (query.isEmpty()) {
            throw new InvalidChatRequestException("query must not be blank");
        }
        KnowledgeBase knowledgeBase = knowledgeBasePort.findById(command.knowledgeBaseId())
                .filter(base -> base.userId() == command.enterpriseUserId())
                .orElseThrow(() -> new NotFoundException("knowledge base not found"));
        int topK = command.topK() == null || command.topK() <= 0
                ? ragConfig.defaultTopK()
                : Math.min(command.topK(), MAX_TOP_K);

        float[] queryVector = embeddingModelPort.embed(query);
        List<VectorSearchHit> hits = vectorStorePort.search(queryVector, topK, knowledgeBase.id());
        Map<String, String> documentNames = documentNames(knowledgeBase.id());
        List<InternalKnowledgeHit> mapped = new ArrayList<>();
        for (VectorSearchHit hit : hits) {
            mapped.add(new InternalKnowledgeHit(
                    hit.documentId(),
                    documentNames.getOrDefault(hit.documentId(), ""),
                    hit.chunkIndex(),
                    hit.content(),
                    hit.score()));
        }
        log.info("[enterprise-internal] search enterpriseUserId={} knowledgeBaseId={} topK={} hits={}",
                command.enterpriseUserId(), knowledgeBase.id(), topK, mapped.size());
        return new InternalKnowledgeResult(command.enterpriseUserId(), toView(knowledgeBase), mapped);
    }

    private InternalKnowledgeBaseView toView(KnowledgeBase knowledgeBase) {
        int documentCount = documentPort.listByKnowledgeBase(knowledgeBase.id()).size();
        return new InternalKnowledgeBaseView(
                knowledgeBase.id(), knowledgeBase.name(), knowledgeBase.description(), documentCount);
    }

    private Map<String, String> documentNames(long knowledgeBaseId) {
        Map<String, String> names = new HashMap<>();
        for (DocumentMetadata metadata : documentPort.listByKnowledgeBase(knowledgeBaseId)) {
            names.put(metadata.documentId(), metadata.name());
        }
        return names;
    }
}
