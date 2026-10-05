#!/usr/bin/env python3
"""Langfuse 初始化脚本（Week 18 交付物）：灌托管 Prompt + 配模型价格。

为什么需要它：新环境起来后 Langfuse 是空的 —— 应用侧会「回退本地模板」、`prompt_name` 为空、
成本只能靠服务端推断。手工用 curl 灌数据时最容易踩的坑是**价格单位**：
Langfuse 存的是「每 token」价，把「0.27 USD / 1M」直接填成 0.27 会让成本放大 1e6 倍。
本脚本把换算固化下来（入参按「每 1M」给，内部除以 1e6）。

用法（在 Langfuse 主机上执行，密钥只走环境变量）：

    export LF_PK=pk-lf-...  LF_SK=sk-lf-...
    python3 seed-langfuse.py --prompt-dir ./prompts            # 灌数据
    python3 seed-langfuse.py --check                           # 只检查现状，不改任何东西

Prompt 文件命名与 classpath 模板一致：`<name>-<version>.txt`（例如 medical-report-v1.txt），
脚本按文件名推导 prompt 名并打上 `production` 标签（应用默认拉这个标签）。
"""

import argparse
import base64
import json
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

PROMPT_FILE_PATTERN = re.compile(r"^(?P<name>[A-Za-z0-9._-]+)-v[0-9A-Za-z._-]+\.txt$")


def api(host, auth, method, path, payload=None):
    """调用 Langfuse Public API；错误以 (status, body) 返回而不是抛异常。"""
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(host + path, data=data, method=method)
    req.add_header("Authorization", auth)
    if data is not None:
        req.add_header("Content-Type", "application/json; charset=utf-8")
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            body = resp.read().decode("utf-8")
            return resp.status, (json.loads(body) if body.strip() else {})
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", "ignore")


def upload_prompts(host, auth, prompt_dir):
    """把目录下的 <name>-<version>.txt 作为 text 型 Prompt 上传（同名会新增版本）。"""
    directory = Path(prompt_dir)
    if not directory.is_dir():
        print(f"  prompt 目录不存在，跳过：{directory}")
        return
    files = sorted(p for p in directory.iterdir() if p.is_file() and p.suffix == ".txt")
    if not files:
        print(f"  目录里没有 *.txt：{directory}")
        return
    for path in files:
        matched = PROMPT_FILE_PATTERN.match(path.name)
        if not matched:
            print(f"  跳过（命名不符合 <name>-<version>.txt）：{path.name}")
            continue
        name = matched.group("name")
        text = path.read_text(encoding="utf-8").strip()
        status, body = api(host, auth, "POST", "/api/public/v2/prompts", {
            "name": name, "type": "text", "prompt": text, "labels": ["production"],
        })
        if status in (200, 201):
            print(f"  {name}: version={body.get('version')} labels={body.get('labels')}")
        else:
            print(f"  {name}: 失败 status={status} {str(body)[:160]}")


def upsert_model(host, auth, model_name, input_per_1m, output_per_1m, unit):
    """按「每 token」口径写入模型价格；同名旧定义先删除（幂等）。"""
    status, body = api(host, auth, "GET", "/api/public/models")
    if status != 200:
        print(f"  读取模型列表失败 status={status}")
        return
    for row in body.get("data", []):
        if isinstance(row, dict) and row.get("modelName") == model_name:
            api(host, auth, "DELETE", f"/api/public/models/{row['id']}")
            print(f"  删除旧定义 id={row['id']}（原 inputPrice={row.get('inputPrice')}）")

    pattern = "(?i)^(%s)$" % re.escape(model_name)
    status, body = api(host, auth, "POST", "/api/public/models", {
        "modelName": model_name,
        "matchPattern": pattern,
        "unit": unit,
        "inputPrice": input_per_1m / 1_000_000,
        "outputPrice": output_per_1m / 1_000_000,
    })
    if status in (200, 201):
        print(f"  {model_name}: 已写入 inputPrice={body.get('inputPrice')} outputPrice={body.get('outputPrice')} "
              f"(= {input_per_1m} / {output_per_1m} USD per 1M tokens)")
    else:
        print(f"  {model_name}: 失败 status={status} {str(body)[:200]}")


def check(host, auth, model_names):
    """只读检查：项目 / Prompt / 模型价格。"""
    status, body = api(host, auth, "GET", "/api/public/projects")
    projects = body.get("data", []) if status == 200 else []
    print(f"  项目：{status} {[p.get('name') for p in projects if isinstance(p, dict)]}")

    status, body = api(host, auth, "GET", "/api/public/v2/prompts")
    rows = body.get("data", []) if status == 200 else []
    print(f"  Prompt：{status} 共 {len(rows)} 个")
    for row in rows:
        if isinstance(row, dict):
            print(f"   - {row.get('name')} labels={row.get('labels')}")

    status, body = api(host, auth, "GET", "/api/public/models")
    hits = [r for r in body.get("data", [])
            if isinstance(r, dict) and r.get("modelName") in model_names]
    for row in hits:
        per_1m_in = float(row.get("inputPrice") or 0) * 1_000_000
        per_1m_out = float(row.get("outputPrice") or 0) * 1_000_000
        flag = "" if per_1m_in < 100 else "  ← 疑似按「每 1M」误填（会放大 1e6 倍）"
        print(f"  {row.get('modelName')} inputPrice={row.get('inputPrice')} outputPrice={row.get('outputPrice')}"
              f"  (= {per_1m_in:g} / {per_1m_out:g} USD per 1M){flag}")


def main():
    parser = argparse.ArgumentParser(description="Langfuse 初始化：托管 Prompt + 模型价格")
    parser.add_argument("--host", default=os.environ.get("LF_HOST", "http://localhost:3000"))
    parser.add_argument("--prompt-dir", default="./prompts", help="<name>-<version>.txt 所在目录")
    parser.add_argument("--model-name", default="deepseek-v4-pro")
    parser.add_argument("--input-usd-per-1m", type=float, default=0.27)
    parser.add_argument("--output-usd-per-1m", type=float, default=1.10)
    parser.add_argument("--unit", default="TOKENS",
                        choices=["TOKENS", "CHARACTERS", "MILLISECONDS", "SECONDS", "REQUESTS", "IMAGES"])
    parser.add_argument("--check", action="store_true", help="只检查现状，不写入")
    args = parser.parse_args()

    pk, sk = os.environ.get("LF_PK"), os.environ.get("LF_SK")
    if not pk or not sk:
        sys.exit("缺少环境变量 LF_PK / LF_SK（Langfuse 项目的 public / secret key）")
    auth = "Basic " + base64.b64encode(f"{pk}:{sk}".encode()).decode()

    if args.check:
        print("== 现状检查 ==")
        check(args.host, auth, {args.model_name})
        return

    print("== 上传托管 Prompt ==")
    upload_prompts(args.host, auth, args.prompt_dir)
    print("== 写入模型价格（内部按每 token 换算）==")
    upsert_model(args.host, auth, args.model_name,
                 args.input_usd_per_1m, args.output_usd_per_1m, args.unit)
    print("== 复核 ==")
    check(args.host, auth, {args.model_name})


if __name__ == "__main__":
    main()
