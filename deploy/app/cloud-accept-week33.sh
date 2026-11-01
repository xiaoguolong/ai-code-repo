#!/usr/bin/env bash
# =============================================================================
# Week 33 真机验收：20 页 PDF 分批 OCR（8/8/4），pageCount 应 = 20
# 前置：20 页 PDF 已上传到 /tmp/w33-guide-20p.pdf（cloud 主机）
# 用法：remote.ps1 -Script .\deploy\app\cloud-accept-week33.sh
# 说明：千帆配额 429 时会 FAILED，等配额恢复后重跑；分批拆分本身由单测覆盖。
# =============================================================================
set -uo pipefail
PDF=/tmp/w33-guide-20p.pdf
[ -s "$PDF" ] || { echo "ERROR: $PDF 缺失"; exit 1; }
python3 - "$PDF" <<'PY'
import json, sys, time, urllib.request, uuid

BASE = "http://127.0.0.1:8081"
def call(m, p, b=None, t=None, ctype="application/json", raw=None):
    h = {}
    if t:
        h["satoken"] = t
    data = raw
    if raw is not None:
        h["Content-Type"] = ctype
    elif b is not None:
        data = json.dumps(b).encode()
        h["Content-Type"] = ctype
    req = urllib.request.Request(BASE + p, data=data, headers=h, method=m)
    with urllib.request.urlopen(req, timeout=600) as resp:
        return json.load(resp)

def multipart(name, payload):
    b = "----w33" + uuid.uuid4().hex
    body = (f"--{b}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{name}\"\r\n"
            f"Content-Type: application/pdf\r\n\r\n").encode() + payload + f"\r\n--{b}--\r\n".encode()
    return body, f"multipart/form-data; boundary={b}"

token = call("POST", "/api/v1/auth/login", {"username": "admin", "password": "admin123"})["data"]["token"]
payload = open(sys.argv[1], "rb").read()
body, ctype = multipart("儿童感冒知识库-20页.pdf", payload)
job = call("POST", "/api/v1/knowledge-bases/1/ingest-jobs", t=token, raw=body, ctype=ctype)["data"]
print("job:", job["jobId"], "duplicate:", job["duplicate"])
detail = None
for _ in range(400):
    detail = call("GET", f"/api/v1/ingest-jobs/{job['jobId']}", t=token)["data"]
    if detail["status"] in ("SUCCEEDED", "FAILED", "CANCELLED"):
        break
    time.sleep(3)
print("status:", detail["status"], "pages:", detail["pageCount"], "chunks:", detail["chunkCount"],
      "error:", detail["errorMessage"])
for s in detail["steps"]:
    print(f"  {s['stepKey']:6s} {s['status']:9s} {s['durationMs']:>7}ms ptok={s['promptTokens']} ctok={s['completionTokens']} | {s['outputSummary']}")
ok = (detail["status"] == "SUCCEEDED" and detail["pageCount"] == 20 and detail["chunkCount"] > 0
      and any("分批=3" in s["outputSummary"] for s in detail["steps"]))
print("RESULT", "PASS" if ok else "FAIL")
PY
