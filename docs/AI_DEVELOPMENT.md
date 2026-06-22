# AI Development Layer (REQ-FW-40…44)

Platform-side development infrastructure for solution developers. AI reads curated context, generates **declarative bundle JSON**, runs validation gates, and publishes through the existing deploy API. The **tree-first agent** (FW-44) operates on the live object tree step-by-step.

**North star:** AI does not write Java/React in `main`; only validated artifacts (bundle, models, dashboards, functions, events) and tree nodes via platform tools.

See [ADR-0011](decisions/0011-ai-artifact-generation-gates.md) and [ADR-0012](decisions/0012-tree-first-ai-agent.md).

---

## Components

| ID | Component | Location |
|----|-----------|----------|
| FW-40 | `LlmProvider` SPI | `packages/ispf-ai-api`, adapters `ispf-ai-openai-compatible`, `ispf-ai-ollama` |
| FW-41 | ContextPack | `ai/context/`, `tools/ai-pack/build.py`, classpath `ai/context-pack.json` |
| FW-42 | ToolRegistry | `com.ispf.server.ai.tool.AiToolRegistry`, REST `/api/v1/ai/tools/**` |
| FW-43 | Platform Studio | Web Console tab **AI Studio** (`apps/web-console`) |
| FW-44 | Tree-first agent | `com.ispf.server.ai.agent.*`, REST `/api/v1/ai/agent/**` |

---

## ContextPack (FW-41)

Build from docs and `examples/*/bundle.json`:

```bash
python tools/ai-pack/build.py
```

Outputs:

- `ai/context/generated/ispf-context-pack.json`
- `packages/ispf-server/src/main/resources/ai/context-pack.json`

Pack includes bundle schema fields, script steps, widget types, API doc slices, and reference examples.

---

## ToolRegistry (FW-42)

Admin-only REST API:

| Endpoint | Purpose |
|----------|---------|
| `GET /api/v1/ai/tools/context-pack` | Context pack metadata |
| `POST /api/v1/ai/tools/validate-bundle` | Semantic bundle validation (no DB writes) |
| `POST /api/v1/ai/tools/dry-run-deploy` | Validate + list `wouldApply` sections |
| `GET /api/v1/ai/models` | List models from active LLM provider |
| `POST /api/v1/ai/bundles/generate` | Prompt → bundle → validate → dry-run |

Request body for validate/dry-run:

```json
{
  "appId": "warehouse",
  "manifest": { "version": "1.0.0", "...": "..." }
}
```

**Acceptance path:** prompt → generated JSON → `validate-bundle` → CI green → `POST /api/v1/platform/packages/import`.

Audit log: table `ai_tool_audit` (migration `V37__ai_tool_audit.sql`).

---

## Tree-first agent (FW-44)

ReAct loop on the platform with bounded steps (default 18, `ispf.ai.agent-max-steps`). Admin-only.

**Multi-turn sessions** (Cursor-like): each chat is a server-side in-memory session with turn history replayed to the LLM (compact user/assistant summaries, no raw tool JSON). `AgentRunState` (validate → import gates) persists for the whole chat.

| Endpoint | Purpose |
|----------|---------|
| `GET /api/v1/ai/agent/tools` | Catalog of platform tools |
| `POST /api/v1/ai/agent/sessions` | Create empty session `{ sessionId, title: "New chat", ... }` |
| `GET /api/v1/ai/agent/sessions/{id}` | Restore chat turns for UI (403/404 if wrong actor or expired) |
| `POST /api/v1/ai/agent/sessions/{id}/messages` | Send message → run turn → `{ summary, steps, status, turnId }` |
| `DELETE /api/v1/ai/agent/sessions/{id}` | Delete session (clear context) |
| `POST /api/v1/ai/agent/run` | **Deprecated** one-shot run (no session store); prefer sessions API |

Create session:

```json
{ "rootPath": "root" }
```

Send message:

```json
{
  "message": "Создай SNMP localhost и дашборд с CPU",
  "rootPath": "root"
}
```

Legacy one-shot run (still supported):

```json
{
  "goal": "List devices under root.platform.devices and create lab-sensor-1",
  "rootPath": "root"
}
```

Response includes `steps[]` (tool calls + results), `summary`, and `status` (`OK` when the model emitted `finish`).

Platform tools (Java handlers, ACL-aware):

| Tool | Purpose |
|------|---------|
| `search_context` | Query ContextPack docs and examples |
| `list_objects` | List tree children |
| `get_object` | Read one node |
| `create_object` | Create DEVICE, DASHBOARD, CUSTOM, … |
| `list_variables` | Read object variables + values |
| `set_variable` | Update variable (config, dashboard layout, …) |
| `configure_driver` | SNMP/driver config + mappings + optional start |
| `driver_control` | start / stop / poll / status |
| `validate_bundle` | ADR-0011 gate (no DB writes) |
| `dry_run_deploy` | Validate + `wouldApply` |
| `import_package` | Deploy bundle (requires prior validate/dry-run OK in same run) |

LLM replies with one JSON object per turn: `{"type":"tool","name":"...","arguments":{...}}` or `{"type":"finish","summary":"...","result":{...}}`.

Configure max steps:

```yaml
ispf:
  ai:
    agent-max-steps: 18
    agent-session-ttl-hours: 24
    agent-max-history-turns: 50
```

`max-tokens` (default **16384**, env `ISPF_AI_MAX_TOKENS`) — лимит **ответа** на один вызов (`max_tokens` в API), не размер всего окна. У Qwen/vLLM окно **~256k** суммарно (промпт + ответ); не ставьте `max_tokens` равным 262144 — иначе под промпт не останется места.

Sessions are **in-memory** on the server (TTL default 24h). JVM restart or TTL expiry drops server state; the Web Console keeps a chat index in `localStorage` and shows “session expired” when `GET session` returns 404.

---

## LlmProvider SPI (FW-40)

Configure via `application.yml` or env:

```yaml
ispf:
  ai:
    enabled: true
    provider: noop            # noop | openai-compatible | ollama | custom-url
    base-url: https://api.openai.com/v1
    model: gpt-4o-mini
    api-key-env: OPENAI_API_KEY
    timeout-seconds: 60
    max-tokens: 16384
    temperature: 0.2
```

| Provider | `base-url` example |
|----------|-------------------|
| `openai-compatible` | `https://api.openai.com/v1` |
| `ollama` | `http://localhost:11434` |
| `custom-url` | Any OpenAI-compatible endpoint |
| `noop` | Default — validate/dry-run work; generate returns 503 |

API keys are read from env var name in `api-key-env`; never stored in audit log.

---

## Platform Studio (FW-43)

Web Console → **AI Studio** tab (admin only):

**Tree-first agent** (default tab):

1. Sidebar lists chats; **New chat** creates a fresh session
2. Опишите задачу обычным языком — follow-up messages keep context in the same chat
3. Агент выполняет шаги на платформе и отвечает понятным текстом
4. В «Подробности» — список шагов; ссылки на устройство и дашборд
5. Delete chat or New chat clears server context for that thread

Пример: *«Создай SNMP localhost, метрики CPU/RAM/сеть и дашборд»* — устройство `snmp-localhost`, драйвер `snmp`, дашборд `snmp-host-monitoring`.

**Bundle generate**:

1. Choose `appId`
2. Enter prompt → **Generate bundle** (requires configured LLM)
3. **Validate** / **Dry-run deploy**
4. **Publish** → `POST /api/v1/platform/packages/import`
5. **Preview operator** → `?mode=operator&app={appId}`

Studio does not add new bundle sections; it uses the same manifest contract as manual import.

---

## Bundle contract

AI output must conform to [SOLUTION_DEVELOPER_PUBLIC_API.md](SOLUTION_DEVELOPER_PUBLIC_API.md). Optional provenance in `metadata`:

```json
{
  "metadata": {
    "generatedBy": "ai-studio",
    "contextPackVersion": "ispf-0.1.0-SNAPSHOT",
    "promptId": "..."
  }
}
```

Commercial bundles: sign **after** AI edits (`contentSha256` covers manifest body).

---

## Related docs

- [PLUGINS.md](PLUGINS.md) — LLM provider outside core (like drivers)
- [APPLICATIONS.md](APPLICATIONS.md) — deploy API
- [DASHBOARDS.md](DASHBOARDS.md) — widget registry for generated dashboards
- [PLATFORM_DEVELOPER_BACKLOG.md §12.7](PLATFORM_DEVELOPER_BACKLOG.md#127-req-fw-4043--ai-development-layer)
