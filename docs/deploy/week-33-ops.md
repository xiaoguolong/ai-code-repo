# Week 33 运维：分批 OCR 与指标

## 1. 发布

```powershell
.\run-maven-jdk17.ps1 -pl apps/enterprise-knowledge-agent -am -DskipTests package
.\remote.ps1 -Put "apps\enterprise-knowledge-agent\target\enterprise-knowledge-agent-0.1.0-SNAPSHOT.jar /tmp/enterprise.jar.new"
.\remote.ps1 -Script .\deploy\app\cloud-week32.sh
# 前端（平台绑定页提示）
Push-Location apps\web\dist; tar -czf "$env:TEMP\web-w33.tar.gz" .; Pop-Location
.\remote.ps1 -Put "$env:TEMP\web-w33.tar.gz /tmp/web.tar.gz"
.\remote.ps1 -Script .\deploy\app\cloud-week32-web.sh
```

## 2. 配置（`.env`，改完重启 enterprise）

| 键 | 默认 | 说明 |
|----|------|------|
| `INGEST_OCR_BATCH_PAGES` | `8` | 每批 OCR 页数（≤10） |
| `INGEST_CLEAN_MODEL` | 空 | 清洗模型；建议 `deepseek-chat`，失败回退 `LLM_MODEL` |
| `INGEST_MAX_PAGES` | `200` | 单文档总页数上限 |
| `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` | `health,info,prometheus` | actuator 暴露项 |

## 3. 指标抓取

```yaml
# prometheus.yml
- job_name: enterprise-ingest
  static_configs:
    - targets: ["127.0.0.1:8081"]
  metrics_path: /actuator/prometheus
```

常用查询：

```promql
sum by (status) (rate(ingest_job_total[10m]))
histogram_quantile(0.95, sum by (le) (rate(ingest_step_duration_seconds_bucket{step="ocr"}[10m])))
```

## 4. 清空 prompt（治理）

```bash
curl -X DELETE -H "X-Admin-Token: $ADMIN_TOKEN" \
  http://127.0.0.1:8081/api/v1/admin/ingest-jobs/<jobId>/prompt
```

返回清空的步骤数；token/耗时/版本保留。令牌未配置时接口一律 403。

## 5. 启动恢复

重启 enterprise 时自动把遗留 `RUNNING/PENDING` 置 `FAILED`（日志
`[ingest] 启动恢复：N 个 ...`），页面可重试。不会自动重跑，避免重启风暴。

## 6. 已知限制

- 千帆配额 429 时整批失败（当前不自动退避），需要人工重试；
- 拆分粒度整批，单页失败会导致整步重跑（P1：按批续跑）；
- 扫描版 PDF 的版面/表格仍按 OCR markdown 原样入库。
