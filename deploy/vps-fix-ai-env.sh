#!/bin/bash
set -euo pipefail
ENV="/opt/ispf/ispf-server.env"
python3 <<'PY'
path = "/opt/ispf/ispf-server.env"
updates = {
    "ISPF_AI_MAX_TOKENS": "65536",
    "ISPF_AI_AGENT_MAX_TOKENS": "131072",
    "ISPF_AI_AGENT_MAX_STEPS": "256",
    "ISPF_AI_AGENT_PARSE_RETRIES": "5",
    "ISPF_AI_AGENT_MAX_TEXT_INJECT_CHARS": "524288",
    "ISPF_AI_AGENT_MAX_ATTACHMENT_BYTES": "33554432",
    "ISPF_AI_AGENT_MAX_HISTORY_TURNS": "128",
    "ISPF_AI_TIMEOUT_SECONDS": "600",
}
lines = open(path, encoding="utf-8", errors="replace").read().splitlines()
out = []
seen = set()
for line in lines:
    if not line.strip() or line.strip().startswith("#"):
        out.append(line)
        continue
    if "=" not in line:
        out.append(line)
        continue
    key = line.split("=", 1)[0]
    if key in updates:
        if key not in seen:
            out.append(f"{key}={updates[key]}")
            seen.add(key)
        continue
    out.append(line)
for key, value in updates.items():
    if key not in seen:
        out.append(f"{key}={value}")
open(path, "w", encoding="utf-8").write("\n".join(out) + "\n")
print("AI env updated")
PY
grep '^ISPF_AI_' "$ENV" | sed 's/ISPF_AI_API_KEY=.*/ISPF_AI_API_KEY=***/'
systemctl restart ispf-server
sleep 12
curl -s http://127.0.0.1:8080/api/v1/info
