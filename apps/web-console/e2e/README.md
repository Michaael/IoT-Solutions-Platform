# Web Console E2E (Playwright)

Admin smoke tests for login, Explorer navigation, and operator deep links. Mocked API routes run against the Vite dev server; optional live tests hit a real ISPF backend.

## Prerequisites

- Node.js 22+
- From repo root: `cd apps/web-console`

## First-time setup

```bash
npm ci
npm run test:e2e:install
```

## Coverage (mocked)

| Scenario | Spec |
| -------- | ---- |
| Login form | `login page` |
| Explorer shell + tree | `admin explorer`, `explorer device variables` |
| Device selection (`?path=` deep link) | `selects a device in the tree` |
| Dashboard layout API | `dashboard preview` |
| Operator `?mode=operator&app=demo` | `operator deep link` |

**In progress:** inspector Variables tab + Dashboard builder UI render (React Query / lazy-tree timing).

## Run mocked smoke tests (default)

Starts Vite on `http://127.0.0.1:5173` automatically unless `E2E_BASE_URL` is set:

```bash
npm run test:e2e
```

Interactive UI:

```bash
npm run test:e2e:ui
```

## Run against a live ISPF server

Point Playwright at a running backend (jar + built UI, or `vite preview` proxied to the API):

```bash
# Windows PowerShell
$env:E2E_BASE_URL="http://localhost:8080"
$env:E2E_USERNAME="admin"
$env:E2E_PASSWORD="admin"
npm run test:e2e
```

```bash
# bash
E2E_BASE_URL=http://localhost:8080 E2E_USERNAME=admin E2E_PASSWORD=admin npm run test:e2e
```

When `E2E_BASE_URL` is set, Playwright does not start the dev server. The optional **live backend** test runs only when both `E2E_USERNAME` and `E2E_PASSWORD` are set.

## CI

GitHub Actions job `web-console` in [`.github/workflows/ci.yml`](../../../.github/workflows/ci.yml) runs `npm test`, `npm run test:e2e`, and `npm run build`.
