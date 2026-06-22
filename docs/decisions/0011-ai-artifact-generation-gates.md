# ADR-0011: AI artifact generation and validation gates

## Status

Accepted (2026-06-22)

## Context

REQ-FW-40…43 introduce an AI Development Layer for solution developers. Without constraints, LLM output could bypass ADR-0008 (no solution Java in `ispf-server`) or deploy invalid/partial bundles.

## Decision

1. **LLM providers live outside `ispf-server` core** via `LlmProvider` SPI (`packages/ispf-ai-api` + adapter modules), configured by Spring profile/env.
2. **AI generates only declarative artifacts** — bundle JSON (migrations, functions, dashboards, `operatorUi`, `events`, etc.). No React/Java in platform `main`.
3. **Mandatory gates before publish:**
   - `validate_bundle` — semantic checks (scripts, SQL guards, events, layout JSON)
   - `dry_run_deploy` — same validation + `wouldApply` plan without DB mutation
4. **ContextPack is dev-time, not a bundle section** — built from docs/examples (`tools/ai-pack/build.py`), not deployed with apps.
5. **Platform Studio is a thin admin UI** over existing tools and `POST /api/v1/platform/packages/import`.
6. **Audit** — `ai_tool_audit` records tool name, actor, request hash, status; no API keys or raw secrets.

## Consequences

- Validate/dry-run work with `provider: noop` (no external LLM required for CI).
- Generate endpoint requires configured provider; failures return clear 503/400.
- Bundle manifest semver table in public API doc unchanged; AI uses optional `metadata` for provenance.
- Commercial license signing must happen after final AI-edited manifest.

## Alternatives considered

| Alternative | Rejected because |
|-------------|------------------|
| Embed OpenAI SDK in `ispf-server` | Violates driver-like boundary; bloats core |
| Auto-deploy without validation | Partial deploy + invalid scripts risk production |
| New `aiHints[]` bundle section | Unnecessary contract churn; ContextPack is sufficient |
