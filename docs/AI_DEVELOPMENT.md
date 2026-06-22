# AI Development Layer (REQ-FW-40…43)

Platform-side development infrastructure for solution developers. AI reads curated context, generates **declarative bundle JSON**, runs validation gates, and publishes through the existing deploy API.

**North star:** AI does not write Java/React in `main`; only validated artifacts (bundle, models, dashboards, functions, events).

See [ADR-0011](decisions/0011-ai-artifact-generation-gates.md).

---

## Components

| ID | Component | Location |
|----|-----------|----------|
| FW-40 | `LlmProvider` SPI | `packages/ispf-ai-api`, adapters `ispf-ai-openai-compatible`, `ispf-ai-ollama` |
| FW-41 | ContextPack | `ai/context/`, `tools/ai-pack/build.py`, classpath `ai/context-pack.json` |
| FW-42 | ToolRegistry | `com.ispf.server.ai.tool.AiToolRegistry`, REST `/api/v1/ai/tools/**` |
| FW-43 | Platform Studio | Web Console tab **AI Studio** (`apps/web-console`) |

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
    max-tokens: 4096
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
