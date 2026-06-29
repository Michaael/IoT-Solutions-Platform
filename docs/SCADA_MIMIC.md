# SCADA mimic diagrams

Configurable SCADA / P&ID / single-line diagrams with a symbol library, live variable bindings, and a visual editor.

See also: [WIDGETS.md § scada-mimic](WIDGETS.md#scada-mimic--scada-мнемосхема).

## Concepts

| Concept | Description |
|---------|-------------|
| `scada-mimic` widget | Dashboard widget that renders a mimic document |
| `MIMIC` object | Reusable diagram stored at `root.platform.mimics.*` (`mimic-v1` model) |
| `diagramJson` | JSON document: symbols, connections, bindings, format rules |
| `grid.snap` | When `true`, placement snaps to `grid.size` (default **off** — pixel coordinates) |
| `grid.visible` | Show editor grid overlay (default **off**) |
| Symbol registry | ~50 inline SVG symbols (process, electrical, common) |

## Document schema (v1)

```json
{
  "version": 1,
  "width": 1600,
  "height": 900,
  "grid": { "size": 20, "snap": false, "visible": false },
  "layers": [{ "id": "layer-default", "name": "Main", "visible": true }],
  "elements": [{
    "id": "t1",
    "symbolId": "tank.vertical",
    "layerId": "layer-default",
    "x": 100,
    "y": 80,
    "bindings": {
      "fillLevel": {
        "objectPath": "root.platform.devices.demo-sensor-01",
        "variableName": "level",
        "valueField": "value",
        "transform": "number"
      }
    }
  }],
  "connections": []
}
```

## Editor

- **Dashboard Builder:** widget `scada-mimic` → «Open mimic editor»
- **Explorer:** open any `MIMIC` object → full-screen editor
- Tools: select, place symbol, connect (orthogonal), undo/redo, import/export JSON

## API

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/v1/mimics/by-path?path=` | Load mimic |
| PUT | `/api/v1/mimics/by-path/diagram?path=` | Save `diagramJson` |
| PUT | `/api/v1/mimics/by-path/title?path=` | Save title |

## mini-TEC migration

Demo plant single-line diagram:

- Object: `root.platform.mimics.mini-tec-single-line`
- Dashboard `mini-tec-single-line` uses `scada-mimic` with `mimicPath` (legacy `mini-tec-sld` widget remains for compatibility)

## Transneft Omsk demo

Industrial tank-farm mimic inspired by a classic oil-trunk pipeline HMI (static showcase values):

- Object: `root.platform.mimics.transneft-omsk-rdp`
- Dashboard: `root.platform.dashboards.transneft-omsk-rdp`
- Template source: `apps/web-console/src/scada/templates/transneftOmskMimic.ts`
- Widget editor: **Insert Transneft Omsk template** (or set `mimicPath` above)

Re-export server JSON after template edits:

```bash
cd apps/web-console && npx tsx -e "import { TRANSNEFT_OMSK_DOCUMENT_JSON } from './src/scada/templates/transneftOmskMimic.ts'; import fs from 'fs'; fs.writeFileSync('../../packages/ispf-server/src/main/resources/bootstrap/transneft-omsk-mimic.json', TRANSNEFT_OMSK_DOCUMENT_JSON)"
```

## Symbol categories

- **process:** tanks, valves, pumps, pipes, sensors, pipeline track, compressors, …
- **electrical:** generators, breakers, busbars, transformers, motors, …
- **common:** labels, tables, alarm banner, shapes

Extend the catalog in `apps/web-console/src/scada/symbols/`.
