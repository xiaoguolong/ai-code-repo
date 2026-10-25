-- Week 24（V8）：随访任务表
-- 语义：审批通过 / Agent 计划生成的随访任务；状态机 PENDING → COMPLETED / CANCELLED。
-- 双库约束（规范 3.7.1）：不使用 JSONB/CLOB/TEXT/部分索引/方言 upsert。
CREATE TABLE IF NOT EXISTS platform_followup_task (
    task_id      VARCHAR(64) PRIMARY KEY,
    tenant_key   VARCHAR(64) NOT NULL,
    user_id      BIGINT      NOT NULL,
    patient_id   VARCHAR(64) NOT NULL,
    source       VARCHAR(16) NOT NULL,
    approval_id  VARCHAR(64),
    execution_id VARCHAR(64),
    title        VARCHAR(200) NOT NULL,
    detail       VARCHAR(1024) NOT NULL DEFAULT '',
    priority     VARCHAR(16) NOT NULL,
    status       VARCHAR(16) NOT NULL,
    due_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    note         VARCHAR(512) NOT NULL DEFAULT '',
    CONSTRAINT fk_platform_followup_task_user FOREIGN KEY (user_id) REFERENCES platform_user(id)
);

CREATE INDEX IF NOT EXISTS idx_platform_followup_task_owner
    ON platform_followup_task(tenant_key, user_id, status, due_at);

CREATE INDEX IF NOT EXISTS idx_platform_followup_task_patient
    ON platform_followup_task(tenant_key, patient_id, status, due_at);

-- 同一审批的同一计划条目只生成一次；approval_id 为 NULL 的行（Agent 直跑）不参与唯一约束。
CREATE UNIQUE INDEX IF NOT EXISTS uk_platform_followup_task_source
    ON platform_followup_task(approval_id, title);
