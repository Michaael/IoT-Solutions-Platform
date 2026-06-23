# ADR-0020: Web Console i18n

## Status

Accepted (2026-06-23)

## Context

Web Console UI strings were hardcoded in ~120 TSX files, mostly Russian with mixed English product terms. Operators and admins need a consistent language switch across admin shell, operator HMI, editors, federation, and AI Studio.

## Decision

1. **Library:** `react-i18next` + `i18next` with namespace JSON files under `apps/web-console/src/locales/{locale}/`.
2. **Canonical locale:** **English (`en`)** — new UI keys are added to `en/*.json` only; `fallbackLng: 'en'`.
3. **Supported locales:** `en`, `ru`, `de`, `zh` (Simplified Chinese). Ukrainian is excluded.
4. **Persistence:** `localStorage` key `ispf.ui.locale`, URL query `?lang=`, browser language as initial hint, default `en`.
5. **Switcher:** `LocaleSwitcher` in admin topbar, operator topbar, and login card.
6. **Derived locales:** `ru`, `de`, `zh` generated from `en` via `tools/i18n/generate-locales.py` (Google Translate batch) + glossary in `tools/i18n/glossary.json`.
7. **CI:** `npm run i18n:check` compares key sets across locales (canonical = `en`).

## In scope

Static Web Console UI: labels, buttons, tabs, empty states, validation messages defined in frontend code.

## Out of scope

- Object tree `displayName` and user-authored content
- BPMN diagram text, app manifest / bundle dashboards
- Server `error.message` without error-code mapping
- AI agent reply text (LLM output language follows prompt, not UI locale)

## Consequences

- Adding UI text requires `en` key + `t()` + `npm run i18n:translate` + `npm run i18n:check`.
- Bundle size grows with four locale JSON sets (acceptable for admin console).
- SCADA glossary terms should stay consistent with [GLOSSARY.md](../GLOSSARY.md).

## Related

- [ROADMAP.md § Phase 19](../ROADMAP.md)
- [WEB_CONSOLE.md](../WEB_CONSOLE.md) § Localization
