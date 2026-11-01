package com.aicode.enterprise.application;

import com.aicode.enterprise.domain.model.IngestJob;
import com.aicode.enterprise.domain.model.IngestJobStep;

import java.util.List;

/**
 * 作业详情（Job + Steps）。
 *
 * @param job   作业
 * @param steps 按 step_no 升序的步骤（含 prompt 原文）
 */
public record IngestJobDetail(IngestJob job, List<IngestJobStep> steps) {
}
