package com.aicode.framework.knowledge.domain.port;

import com.aicode.framework.knowledge.domain.model.KnowledgeBaseCatalog;
import com.aicode.framework.knowledge.domain.model.KnowledgeSearchHit;
import com.aicode.framework.knowledge.domain.model.KnowledgeSearchQuery;

import java.util.List;

/**
 * 知识库出站端口（Week 25）。
 *
 * <p>平台不存知识库正文：实现负责调用 enterprise 的内部只读端点。
 * 失败一律抛 {@code ToolExecutionException}（对外 502），**不允许静默返回空结果**——
 * 医疗问答在没有检索结果时给“看起来正常”的答案，比报错更危险。</p>
 */
public interface KnowledgeSearchPort {

    /**
     * 查询平台用户映射到的企业用户名下的知识库（导入用）。
     *
     * @param platformUserId 平台用户 ID
     * @return 本地用户 ID + 知识库清单
     */
    KnowledgeBaseCatalog listBases(long platformUserId);

    /**
     * 按企业本地用户查询其名下知识库（Week 27：绑定对账用）。
     *
     * <p>不依赖平台 ↔ 企业的实时 SSO 映射：绑定里记录的就是 {@code enterpriseUserId}，
     * 导入人离职/解绑不应让对账失效。</p>
     *
     * @param enterpriseUserId 企业本地用户 ID
     * @return 企业用户 ID + 知识库清单
     */
    KnowledgeBaseCatalog listBasesByEnterpriseUser(long enterpriseUserId);

    /**
     * 检索切片。
     *
     * @param query 检索条件
     * @return 命中切片（按相似度倒序），无命中原样返回空列表
     */
    List<KnowledgeSearchHit> search(KnowledgeSearchQuery query);
}
