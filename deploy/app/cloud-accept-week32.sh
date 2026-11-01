#!/usr/bin/env bash
# =============================================================================
# Week 32 真机验收（在云主机本机执行，规避公网抖动）
# -----------------------------------------------------------------------------
# 前置：20 页 PDF 已上传到 /tmp/w32-guide-20p.pdf。
# 覆盖：operator 登录 → 建作业 → 轮询终态 → 节点/耗时/prompt/token 打印
#       → 同文件重复上传幂等 → 纯文本入库回归。
# 说明：千帆单次 PDF 解析上限 10 页，20 页文件会按厂商返回 10 页（分批 OCR 列 P1）；
#       验收断言按"厂商页数 ≥10 且 chunk ≥5"。
# =============================================================================
set -uo pipefail
PDF="${1:-/tmp/w32-guide-20p.pdf}"
[ -s "$PDF" ] || { echo "ERROR: $PDF 缺失"; exit 1; }
python3 - "$PDF" <<'PY'
import json, sys, time, urllib.error, urllib.request, uuid

BASE = "http://127.0.0.1:8081"
PDF = sys.argv[1]


def call(method, path, body=None, token=None, ctype="application/json", raw=None):
    headers = {}
    if token:
        headers["satoken"] = token
    data = raw
    if raw is not None:
        headers["Content-Type"] = ctype
    elif body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = ctype
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=300) as resp:
            return json.load(resp)
    except urllib.error.HTTPError as ex:
        raise SystemExit(f"HTTP {ex.code} {path}: {ex.read().decode('utf-8','replace')}")


def multipart(field, filename, payload):
    boundary = "----w32" + uuid.uuid4().hex
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"{field}\"; filename=\"{filename}\"\r\n"
            f"Content-Type: application/pdf\r\n\r\n").encode() + payload + f"\r\n--{boundary}--\r\n".encode()
    return body, f"multipart/form-data; boundary={boundary}"


login = call("POST", "/api/v1/auth/login", {"username": "operator", "password": "operator123"})
token = login["data"]["token"]
bases = call("GET", "/api/v1/knowledge-bases", token=token)["data"]
kb_id = bases[0]["id"] if bases else call(
    "POST", "/api/v1/knowledge-bases", {"name": "W32 验收库", "description": "20 页 PDF"}, token=token)["data"]["id"]
print("login=ok user=operator kb=", kb_id)

payload = open(PDF, "rb").read()
body, ctype = multipart("file", "w32-guide-20p.pdf", payload)
started = time.time()
created = call("POST", f"/api/v1/knowledge-bases/{kb_id}/ingest-jobs", token=token, raw=body, ctype=ctype)["data"]
job_id = created["jobId"]
print("job=", job_id, "duplicate=", created["duplicate"], "bytes=", len(payload))

detail = None
for _ in range(300):
    detail = call("GET", f"/api/v1/ingest-jobs/{job_id}", token=token)["data"]
    if detail["status"] in ("SUCCEEDED", "FAILED", "CANCELLED"):
        break
    time.sleep(3)
print("status=", detail["status"], "pages=", detail["pageCount"], "chunks=", detail["chunkCount"],
      "elapsedSec=", int(time.time() - started), "error=", detail["errorMessage"])
for s in detail["steps"]:
    print(f"  step{s['stepNo']} {s['stepKey']:6s} {s['status']:9s} {s['durationMs']:>7}ms "
          f"promptLen={len(s['promptText'])} ptok={s['promptTokens']} ctok={s['completionTokens']} | {s['outputSummary']}")

body2, ctype2 = multipart("file", "w32-guide-20p.pdf", payload)
again = call("POST", f"/api/v1/knowledge-bases/{kb_id}/ingest-jobs", token=token, raw=body2, ctype=ctype2)["data"]
print("idempotent: job=", again["jobId"], "duplicate=", again["duplicate"], "same=", again["jobId"] == job_id)

text = call("POST", f"/api/v1/knowledge-bases/{kb_id}/documents",
            {"name": "w32-regression.txt", "content": "糖尿病随访：每 3 个月复查 HbA1c。"}, token=token)["data"]
print("text ingest:", text["documentId"], "chunks=", text["chunkCount"])

clean = next(s for s in detail["steps"] if s["stepKey"] == "clean")
ok = (detail["status"] == "SUCCEEDED" and detail["pageCount"] >= 10 and detail["chunkCount"] >= 5
      and clean["promptTokens"] > 0 and len(clean["promptText"]) > 0
      and again["jobId"] == job_id and text["chunkCount"] >= 1)
print("RESULT", "PASS" if ok else "FAIL")
sys.exit(0 if ok else 1)
PY
