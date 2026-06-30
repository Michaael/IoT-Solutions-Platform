# ADR-0021: Haystack semantic overlay

## Status

Accepted (2026-06-30)

## Context

ISPF models runtime state through the **object tree** (dot-paths, `DataSchema`, RELATIVE/INSTANCE models, bindings, historian). Project Haystack and Brick Schema provide industry-standard semantics for BMS, energy, and digital-twin integrations, but the platform had **no** Haystack/Brick layer in code (discussion only, BL-56…62).

Requirements from app teams and integrators:

- Export equipment/point semantics to external Haystack tools without replacing ISPF paths.
- Optional tagging on devices and telemetry variables; tree remains authoritative for bindings, ACL, and history.
- Demand-driven Brick/RDF export later (BIM / digital twin), not a runtime replacement for the object tree.

Related standards:

| Standard | Role |
| -------- | ---- |
| **Project Haystack** | Tag-based semantic model (`equip`, `point`, `sensor`, `temp`, `his`, …) |
| **Brick Schema** | Formal RDF/TTL class graph (`brick:Equipment`, `hasPoint`) |
| **OPC UA semantic model** | Companion specification nodes — useful reference for mapping, not primary for ISPF |
| **ASHRAE 223P** | BACnet semantic tags — aligns with Haystack tags for BACnet drivers |

## Decision

### 1. Object tree = source of truth

- Runtime paths (`root.platform.devices.*`), bindings, correlators, historian, and ACL continue to use ISPF dot-paths.
- Haystack tags are an **optional metadata overlay** stored as model-managed variables on devices (RELATIVE mixin).
- Haystack refs (`@site.equip`) do **not** replace or redirect ISPF paths unless explicitly bound in a future integration driver (BL-61).

### 2. Haystack overlay model (`haystack-metadata-v1`)

RELATIVE mixin on `DEVICE` objects with variables in group `haystack`:

| Variable | Type | Purpose |
| -------- | ---- | ------- |
| `haystackTags` | STRING (JSON array) | Marker tag names, e.g. `["equip","lab","site"]` |
| `haystackRef` | STRING | Optional external Haystack id |
| `haystackKind` | STRING | Primary kind hint: `equip`, `point`, `site`, … |

Apply manually via `POST /api/v1/relative-models/{id}/apply` or platform bootstrap/demo fixtures.

**Demo:** `root.platform.devices.lab-userA-01` (virtual-lab fixture) ships with equip tags and sample export.

### 3. Haystack export API

`GET /api/v1/platform/haystack/export?rootPath=&includePoints=`

Returns a JSON grid (`formatVersion`, `rows[]`) where each row includes:

- `id`, `path`, `dis` — ISPF identity and display name
- `tags` — marker map derived from `haystackTags`
- `entityKind` — `equip` for tagged devices; `point` for historian-enabled variables when `includePoints=true`
- `curVal`, `unit` — current scalar from variable value (when present)

This is an **ISPF interchange format** inspired by Haystack JSON grids, not a full Zinc/HTTP Haystack server.

### 4. Out of scope (v1)

- Replacing `root.platform.devices.*` paths with Haystack refs in runtime.
- Full Brick reasoner or RDF inference in the platform core.
- Auto-discovery of tags from BACnet/OPC UA without explicit mapping — out of scope; use extended `driverPointMappingsJson` (BL-59 Done).
- External Haystack poll driver (BL-61) — semantic HMI auto-bind (BL-62 Done).

### 5. Brick Schema (BL-60)

Deferred demand-driven overlay: `brickClass` URI on objects, Turtle/JSON-LD export subset, `hasPoint` via explicit refs or bindings. Requires separate REQ from app team per [0002](0002-dogfooding-gate.md).

## Consequences

**Positive**

- Integrators can export tagged subtrees without forking the object model.
- RELATIVE mixin pattern matches existing driver/lab mixins ([0011](0011-model-type-semantics.md), [0018](0018-fixture-models-and-cel-applicability.md)).
- Clear boundary: semantics are metadata; mechanisms stay tree-first.

**Negative / follow-ups**

- Point-level tags are not first-class on each variable yet — use driver mapping JSON (BL-59 Done) or per-variable naming until variable annotations exist.
- Export format may need Zinc compatibility layer if external FIN/SkySpark ingestion is required.

**Update (2026-06-30):** BL-57 Done — dedicated **Haystack** inspector tab with marker multiselect (`HaystackMetadataPanel`).

**Update (2026-06-30):** BL-62 Done — `GET /platform/haystack/search`, agent tool `search_by_haystack_tags`, dashboard «Bind by Haystack tags».

## Implementation map (BL-56…62)

| BL | Deliverable | Status |
| -- | ----------- | ------ |
| BL-56 | This ADR | Done |
| BL-57 | `haystack-metadata-v1` + lab demo + inspector tag editor | Done |
| BL-58 | `GET /api/v1/platform/haystack/export` | Done |
| BL-59 | Extended driver point mappings → Haystack export | Done |
| BL-62 | Tag search + dashboard auto-bind + agent tool | Done |
| BL-60…61 | Brick, external Haystack driver | Planned |

## Related materials

- [CODE_AUDIT_BACKLOG.md § BL-56…62](../CODE_AUDIT_BACKLOG.md#bl-5662--haystack--brick-schema-semantic-layer)
- [MODELS.md](../MODELS.md) — RELATIVE mixins
- [DRIVERS.md](../DRIVERS.md) — point mappings (future Haystack tag normalization)
- [0002 Dogfooding gate](0002-dogfooding-gate.md)
