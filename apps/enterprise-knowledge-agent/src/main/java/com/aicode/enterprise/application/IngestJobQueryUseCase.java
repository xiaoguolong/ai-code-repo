package com.aicode.enterprise.application;

import com.aicode.core.domain.exception.NotFoundException;
import com.aicode.enterprise.domain.exception.ForbiddenException;
import com.aicode.enterprise.domain.model.IngestJob;
import com.aicode.enterprise.domain.model.IngestJobStep;
import com.aicode.enterprise.domain.model.KnowledgeBase;
import com.aicode.enterprise.domain.port.IngestJobPort;
import com.aicode.enterprise.domain.port.KnowledgeBasePort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 入库作业查询（Week 32）：列表 + 详情（Job + Steps），按知识库归属鉴权。
 */
@Service
public class IngestJobQueryUseCase {

    private final IngestJobPort ingestJobPort;
    private final KnowledgeBasePort knowledgeBasePort;

    public IngestJobQueryUseCase(IngestJobPort ingestJobPort, KnowledgeBasePort knowledgeBasePort) {
        this.ingestJobPort = ingestJobPort;
        this.knowledgeBasePort = knowledgeBasePort;
    }

    /**
     * @param kbId   知识库
     * @param userId 当前用户
     * @param limit  条数上限
     * @return 作业列表
     */
    public List<IngestJob> list(long kbId, long userId, int limit) {
        requireOwned(kbId, userId);
        return ingestJobPort.listByKb(kbId, limit);
    }

    /**
     * @param jobId  作业
     * @param userId 当前用户
     * @return 作业 + 步骤（步骤含 prompt 原文）
     */
    public IngestJobDetail detail(String jobId, long userId) {
        IngestJob job = ingestJobPort.findById(jobId)
                .orElseThrow(() -> new NotFoundException("ingest job not found"));
        requireOwned(job.kbId(), userId);
        List<IngestJobStep> steps = ingestJobPort.listSteps(jobId);
        return new IngestJobDetail(job, steps);
    }

    private void requireOwned(long kbId, long userId) {
        KnowledgeBase knowledgeBase = knowledgeBasePort.findById(kbId)
                .orElseThrow(() -> new NotFoundException("knowledge base not found"));
        if (knowledgeBase.userId() != userId) {
            throw new ForbiddenException("access denied");
        }
    }
}
