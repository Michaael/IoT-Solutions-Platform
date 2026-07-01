#!/bin/bash
# Append or update LLM settings in /opt/ispf/ispf-server.env (safe for systemd EnvironmentFile).
set -euo pipefail

ENV_FILE="${ISPF_ENV_FILE:-/opt/ispf/ispf-server.env}"
AI_PROVIDER="${ISPF_AI_PROVIDER:-openai-compatible}"
AI_BASE_URL="${ISPF_AI_BASE_URL:-http://84.42.21.226:8000/v1}"
AI_MODEL="${ISPF_AI_MODEL:-Qwen/Qwen3.6-35B-A3B}"
AI_API_KEY="${ISPF_AI_API_KEY:-}"
AI_TIMEOUT="${ISPF_AI_TIMEOUT_SECONDS:-600}"

if [ ! -f "$ENV_FILE" ]; then
  echo "Creating $ENV_FILE"
  touch "$ENV_FILE"
  chmod 600 "$ENV_FILE"
fi

upsert() {
  local key="$1"
  local value="$2"
  if grep -q "^${key}=" "$ENV_FILE" 2>/dev/null; then
    sed -i "s|^${key}=.*|${key}=${value}|" "$ENV_FILE"
  else
    echo "${key}=${value}" >> "$ENV_FILE"
  fi
}

upsert ISPF_AI_ENABLED true
upsert ISPF_AI_PROVIDER "$AI_PROVIDER"
upsert ISPF_AI_BASE_URL "$AI_BASE_URL"
upsert ISPF_AI_MODEL "$AI_MODEL"
upsert ISPF_AI_TIMEOUT_SECONDS "$AI_TIMEOUT"
upsert ISPF_AI_MAX_TOKENS 65536
upsert ISPF_AI_AGENT_MAX_TOKENS 131072
upsert ISPF_AI_AGENT_MAX_STEPS 256
upsert ISPF_AI_AGENT_PARSE_RETRIES 5
upsert ISPF_AI_AGENT_MAX_TEXT_INJECT_CHARS 524288
upsert ISPF_AI_AGENT_MAX_ATTACHMENT_BYTES 33554432
upsert ISPF_AI_AGENT_MAX_HISTORY_TURNS 128
upsert ISPF_AI_TEMPERATURE 0.2

if [ -n "$AI_API_KEY" ]; then
  upsert ISPF_AI_API_KEY "$AI_API_KEY"
fi

echo "=== LLM env configured in $ENV_FILE ==="
grep '^ISPF_AI_' "$ENV_FILE" | sed 's/ISPF_AI_API_KEY=.*/ISPF_AI_API_KEY=***redacted***/'
