package com.aicode.enterprise.domain.port;

import com.aicode.enterprise.domain.model.IngestJob;
import com.aicode.enterprise.domain.model.IngestJobStep;

import java.util.List;
import java.util.Optional;

/**
 * 入库作业与步骤的持久化端口（Week 32）。
 *
 * <p>作业与步骤分两次写：先建 Job（PENDING），再逐条 upsert Step；步骤更新必须幂等
 * （主键 job_id + step_no）。</p>
 */
public interface IngestJobPort {

    /**
     * 新建作业（PENDING）。重复的 (kbId, contentHash) 由唯一索引拒绝，调用方应先查。
     *
     * @param job 作业
     */
    void create(IngestJob job);

    /**
     * 更新作业（状态/OCR 产物/计数/错误）。
     *
     * @param job 作业
     */
    void update(IngestJob job);

    /**
     * @param jobId 作业 ID
     * @return 作业
     */
    Optional<IngestJob> findById(String jobId);

    /**
     * 幂等查询：同一知识库下内容相同的历史作业。
     *
     * @param kbId        知识库
     * @param contentHash sha256
     * @return 作业
     */
    Optional<IngestJob> findByKbAndHash(long kbId, String contentHash);

    /**
     * @param kbId  知识库
     * @param limit 上限
     * @return 最近的作业（按创建时间倒序）
     */
    List<IngestJob> listByKb(long kbId, int limit);

    /**
     * 插入或更新步骤（job_id + step_no 主键）。
     *
     * @param step 步骤
     */
    void saveStep(IngestJobStep step);

    /**
     * @param jobId 作业
     * @return 按 step_no 升序的步骤
     */
    List<IngestJobStep> listSteps(String jobId);

    /**
     * 启动恢复：把遗留的 RUNNING/PENDING 置 FAILED。
     *
     * @param reason 失败原因（写入 error_message）
     * @return 受影响行数
     */
    int failUnfinished(String reason);

    /**
     * 清空某作业所有步骤的 prompt 原文（保留 token/耗时/版本）。
     *
     * @param jobId 作业
     * @return 清空的步骤数
     */
    int clearPrompt(String jobId);
}
