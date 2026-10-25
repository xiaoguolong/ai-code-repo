#!/usr/bin/env bash
# Week 26 · 为知识库 Agent 3 准备一份可检索的企业侧演示资料（幂等）
#   前置：enterprise 已启 pgvector + V6；SSO_ENABLED=true；平台在跑
#   做什么：以 admin 身份 SSO 登录 enterprise → 建「医疗随访规范库」→ 入一份纯文本文档
#   不打印任何令牌；重复执行只复用，不重复建库/建文档
set -uo pipefail

ENT="http://127.0.0.1:8081"
KB_NAME="医疗随访规范库"
DOC_NAME="糖尿病随访管理规范.txt"
KB_DESC="内分泌科随访制度与质控要求（周 26 演示数据）"

login() {
  curl -s --max-time 20 -X POST "$ENT/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"admin123"}'
}

json() { python3 -c "import sys,json;d=json.load(sys.stdin);print($1)" 2>/dev/null; }

FIRST="$(login)"
TOKEN="$(printf '%s' "$FIRST" | json "d['data']['token']")"
UID="$(printf '%s' "$FIRST" | json "d['data']['user']['userId']")"
if [ -z "$TOKEN" ]; then echo "FAIL: admin SSO 登录失败: $FIRST"; exit 1; fi
echo "enterprise admin: userId=$UID token=yes"

# ── 1. 知识库（按名字幂等）──────────────────────────────────────────────────
LIST="$(curl -s "$ENT/api/v1/knowledge-bases" -H "satoken: $TOKEN")"
KBID="$(printf '%s' "$LIST" | json "next((i['id'] for i in d['data'] if i['name']=='$KB_NAME'), '')")"
if [ -z "$KBID" ]; then
  CREATED="$(curl -s -X POST "$ENT/api/v1/knowledge-bases" -H "satoken: $TOKEN" \
    -H 'Content-Type: application/json' \
    -d "$(python3 -c "import json;print(json.dumps({'name':'$KB_NAME','description':'$KB_DESC'},ensure_ascii=False))")")"
  KBID="$(printf '%s' "$CREATED" | json "d['data']['id']")"
  echo "knowledge base created: id=$KBID"
else
  echo "knowledge base reused: id=$KBID"
fi
[ -n "$KBID" ] || { echo "FAIL: 知识库创建失败"; exit 1; }

# ── 2. 文档（按名字幂等；纯文本，同步切片+向量化）───────────────────────────
DOCS="$(curl -s "$ENT/api/v1/knowledge-bases/$KBID/documents" -H "satoken: $TOKEN")"
HAS_DOC="$(printf '%s' "$DOCS" | json "any(i['name']=='$DOC_NAME' for i in d['data'])")"
if [ "$HAS_DOC" != "True" ]; then
  CONTENT="$(cat <<'DOC'
糖尿病随访管理规范（2026 版）

一、适用范围
本规范适用于已确诊 2 型糖尿病的门诊与出院患者，覆盖内分泌科及协作科室的随访管理。

二、随访频率
1. 血糖控制达标（HbA1c < 7.0%）且近 3 个月无低血糖事件者，每 3 个月随访一次。
2. HbA1c 在 7.0% 至 9.0% 之间者，每 2 个月随访一次；连续两次不达标应缩短至每月一次。
3. HbA1c 大于 9.0%、反复低血糖，或合并感染、酮症者，每 2 周随访一次，必要时转诊内分泌专科。

三、随访必查内容
每次随访必须记录：空腹血糖、餐后 2 小时血糖、体重与 BMI、血压、用药依从性、低血糖发生次数。
每 3 个月检测一次 HbA1c；每 6 个月检查一次尿微量白蛋白/肌酐比值、眼底与足部感觉。

四、生活方式干预
1. 饮食：控制总热量，碳水化合物占 45% 至 60%，减少含糖饮料与精制主食。
2. 运动：每周至少 150 分钟中等强度有氧运动，餐后 1 小时运动有助于降低餐后血糖。
3. 戒烟限酒；BMI 目标小于 24 千克每平方米。

五、用药与安全
1. 二甲双胍为一线用药；eGFR 小于 30 时停用。
2. 使用胰岛素或磺脲类药物者，必须教会患者识别低血糖（心慌、出汗、手抖）并随身携带糖块。
3. 发生需要他人协助的严重低血糖，应在 24 小时内上报并安排复诊。

六、随访提醒与升级
1. 到期未随访 7 天，由随访护士电话提醒；逾期 14 天升级至责任医师。
2. 连续两次未随访或出现急性并发症者，列入重点随访名单，每月随访一次。

七、质控指标
随访率不低于 90%，HbA1c 检测率不低于 85%，低血糖上报率 100%，患者满意度不低于 90%。
DOC
)"
  BODY="$(python3 -c "
import json,sys
print(json.dumps({'name':sys.argv[1],'content':sys.argv[2]},ensure_ascii=False))
" "$DOC_NAME" "$CONTENT")"
  ING="$(curl -s --max-time 180 -X POST "$ENT/api/v1/knowledge-bases/$KBID/documents" \
    -H "satoken: $TOKEN" -H 'Content-Type: application/json' -d "$BODY")"
  DOCID="$(printf '%s' "$ING" | json "d['data']['documentId']")"
  CHUNKS="$(printf '%s' "$ING" | json "d['data']['chunkCount']")"
  echo "document ingested: id=${DOCID:-none} chunks=${CHUNKS:-0}"
  [ -n "$DOCID" ] || { echo "FAIL: 文档入库失败: $ING"; exit 1; }
else
  echo "document reused: $DOC_NAME"
fi

# ── 3. 结果摘要（不打印向量）───────────────────────────────────────────────
echo "-- enterprise document_chunk 行数 --"
set -a; . /opt/ai-code/app/enterprise/.env; set +a
E="${SPRING_DATASOURCE_URL##*/}"; E="${E%%\?*}"
docker exec -e PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" pg16 psql -h 127.0.0.1 \
  -U "$SPRING_DATASOURCE_USERNAME" -d "$E" -tAc \
  "select 'kb='||(select count(*) from knowledge_base)||' docs='||(select count(*) from document)||' chunks='||(select count(*) from document_chunk)||' emb_model_dim='||coalesce((select vector_dims(embedding)::text from document_chunk limit 1),'n/a');"
echo "SEED DONE kbId=$KBID userId=$UID"
