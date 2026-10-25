# 日志归档索引

> 约定：根目录不再堆放 `target-*.log`；所有运行/测试/验收日志统一放到 `logs/`。
> `logs/` 已被 `.gitignore` 忽略（日志体积大、含环境输出），**本索引文件入库**，用于说明目录与证据位置。

## 1. 目录结构

```text
logs/
├── week-20/
│   ├── test/            # 第 20 周根聚合/模块测试
│   └── diagnostics/     # baseline / pair 等按日期归入的诊断日志
├── week-21/
│   ├── test/            # 第 21 周根聚合/模块测试
│   └── postgres/        # 第 21 周真实 PostgreSQL 迁移/启动验证
├── week-22/
│   └── test/            # 第 22 周根聚合测试
├── week-23/
│   ├── test/            # 本地根聚合 / 专项 / 复验
│   ├── deploy/          # V6/V7 发布、备份
│   ├── migration/       # 迁移前巡检、迁移后版本/角色/tenant 校验
│   ├── acceptance/      # 云端 HITL / 批次 2 / 回归验收
│   └── commit/          # 提交输出
├── week-24/
│   └── test/            # 第 24 周根聚合测试（885 用例 + V8 首次迁移）
├── week-25/
│   └── test/            # 第 25 周模块/根聚合测试（924 用例 + V9 首次迁移）
└── legacy/
    └── 2026-10-06/      # 早期未按周命名的 all/gw/plat/package 日志
```

命名保持原文件名，便于与实现日志、聊天记录中的引用一一对应；新日志建议：

```text
logs/week-<NN>/<test|deploy|migration|acceptance|commit>/target-week<NN>-<用途>.log
```

## 2. 第 23 周关键日志

| 日志 | 证明什么 |
|------|----------|
| `logs/week-23/test/target-week23-baseline.log` | 改代码前基线：726 用例全绿 |
| `logs/week-23/test/target-week23-full.log` | 批次 1 后根聚合：777 全绿 + H2 V6 |
| `logs/week-23/test/target-week23-final3.log` | 批次 1 重构后最终回归：777 全绿 |
| `logs/week-23/test/target-week23-batch2-full.log` | 批次 2 后根聚合：798 全绿 + H2 V7 |
| `logs/week-23/test/target-week23-verify-local.log` | 上线后复验：798 全绿、H2 首次应用 7 个迁移 |
| `logs/week-23/deploy/target-week23-deploy.log` | 云端 V6 发布（平台 jar 切换/健康检查） |
| `logs/week-23/deploy/target-week23-b2-deploy-platform.log` | 云端 V7 平台发布 |
| `logs/week-23/deploy/target-week23-b2-deploy-enterprise.log` | enterprise 批次 2 发布 |
| `logs/week-23/deploy/target-week23-b2-backup.log` | V7 前角色四表/执行记录/用户/历史表备份 |
| `logs/week-23/deploy/target-week23-memory-tune.log` | 停 Langfuse web/worker + 降 OAP/UI 堆的前后内存对比 |
| `logs/week-23/deploy/target-week23-memory-tune-langfuse-off.log` | platform 关闭 Langfuse 导出并重启 |
| `logs/week-23/migration/target-week23-cloud-precheck-after3.log` | V6 后：5 条记忆、向量来源、审批/审计 |
| `logs/week-23/migration/target-week23-b2-check.log` | V7 后：default/hospital-b 各 3 角色、执行 tenant 一致 |
| `logs/week-23/migration/target-week23-verify-precheck.log` | 上线后只读巡检（V7 + 资源） |
| `logs/week-23/migration/target-week23-verify-b2check.log` | 上线后角色租户/执行 tenant 复验 |
| `logs/week-23/acceptance/target-week23-cloud-accept.log` | V6 后 HITL 验收 PASS=21 |
| `logs/week-23/acceptance/target-week23-b2-accept.log` | 批次 2 验收 PASS=19（含 409/503） |
| `logs/week-23/acceptance/target-week23-b2-regression-hanitl.log` | V7 后 HITL 回归 PASS=21 |
| `logs/week-23/acceptance/target-week23-verify-hitl.log` | 上线后复验 HITL PASS=21（1807 tokens） |
| `logs/week-23/acceptance/target-week23-verify-b2accept.log` | 上线后复验批次 2 PASS=19 |
| `logs/week-23/commit/target-week23-commit.log` | 主提交 `ee55466` 输出 |
| `logs/week-23/commit/target-week23-verify-commit.log` | 验收记录提交 `723073b` 输出 |

## 3. 第 24 周关键日志

| 日志 | 证明什么 |
|------|----------|
| `logs/week-24/test/target-week24-full.log` | 根聚合 **885** 全绿（ai-core 90 / enterprise 55 / patient 21 / platform 619 / gateway 100），BUILD SUCCESS |
| 同上（`09:48:48` 行） | V8 首次启动 `Successfully applied 8 migrations ... now at version v8`，后续上下文只校验 |
| 同上（`platform` 模块段） | 本周新增 86 例：运营统计/异常/日报、随访计划/任务/状态机/提醒、审批联动、对账、Gauge |

> 云端发布（V8 真库 + `cloud-accept-week24.sh`）尚未执行；执行后按
> `docs/deploy/week-24-ops.md` 第 2–3 节补 `logs/week-24/deploy/`、`migration/`、`acceptance/`。

## 4. 第 25 周关键日志

| 日志 | 证明什么 |
|------|----------|
| `logs/week-25/test/target-week25-full.log` | 根聚合 **924** 全绿（ai-core 90 / enterprise 65 / patient 21 / platform 648 / gateway 100），BUILD SUCCESS |
| 同上（`10:27:42` 行） | V9 首次启动 `Successfully applied 9 migrations ... now at version v9` |

> 云端 pgvector、V6 补跑与 `cloud-accept-week25.sh` 尚未执行；执行后补
> `logs/week-25/deploy/`、`acceptance/`（步骤见 `docs/deploy/week-25-knowledge.md`）。

## 5. 早期日志
| 目录 | 内容 |
|------|------|
| `logs/week-20/test/` | `target-week20-full/full2/final` + 平台/网关分模块测试 |
| `logs/week-20/diagnostics/` | `target-baseline-test`、`target-pair`（按文件时间归入第 20 周） |
| `logs/week-21/test/` | `target-week21-full/final/final2` |
| `logs/week-21/postgres/` | `target-week21-pg..pg5`（真实 PostgreSQL 验证） |
| `logs/week-22/test/` | `target-week22-full/final/final2/final3` |
| `logs/legacy/2026-10-06/` | 早期 `all/gw/plat/package` 测试日志 |

## 6. 维护规则

1. 新日志按周/类别归档，**不要再写仓库根目录**；
2. 日志不得包含真实密钥、密码、完整 Prompt 或患者隐私正文；
3. 需要长期引用的结论写进 `notes/impl-logs/week-XX.md`，日志只作为原始证据；
4. `logs/` 不入库；如换机器需要保留，单独打包或上传对象存储。
