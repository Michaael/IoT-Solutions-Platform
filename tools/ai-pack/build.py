#!/usr/bin/env python3
"""Build ISPF ContextPack for AI Development Layer (FW-41, FW-45)."""

from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs"
EXAMPLES = ROOT / "examples"
OUT_DIR = ROOT / "ai" / "context" / "generated"
SERVER_RESOURCE = (
    ROOT
    / "packages"
    / "ispf-server"
    / "src"
    / "main"
    / "resources"
    / "ai"
    / "context-pack.json"
)

SCRIPT_STEPS = [
    "selectOne",
    "selectMany",
    "exec",
    "setVar",
    "buildRecord",
    "map",
    "when",
    "if",
    "invoke_function",
    "cancel_workflows",
    "failIfNull",
    "failIfNotEquals",
    "return",
]

WIDGET_TYPES = [
    "value",
    "toggle",
    "indicator",
    "chart",
    "sparkline",
    "progress",
    "gauge",
    "linear-gauge",
    "liquid-gauge",
    "status-badge",
    "function",
    "function-form",
    "pie-chart",
    "history-table",
    "variable-editor",
    "svg-widget",
    "composite-widget",
    "dashboard-link",
    "event-feed",
    "work-queue",
    "object-table",
    "card-grid",
    "map",
    "object-tree",
    "report",
    "sub-dashboard",
    "panel",
    "tab-panel",
    "label",
    "image",
    "html-snippet",
    "breadcrumbs",
    "timer",
    "context-list",
    "input-form",
    "drawer-panel",
    "carousel",
    "steps-panel",
    "gantt-chart",
    "network-graph",
    "spreadsheet",
    "nav-menu",
]

BUNDLE_MANIFEST_FIELDS = [
    "version",
    "displayName",
    "tablePrefix",
    "schemaName",
    "objects",
    "dashboards",
    "workflows",
    "models",
    "migrations",
    "functions",
    "bindings",
    "reports",
    "alertRules",
    "correlators",
    "schedules",
    "events",
    "requires",
    "license",
    "metadata",
    "operatorUi",
    "operatorManifest",
]

EXAMPLE_PURPOSES = {
    "demo-app": "Minimal bundle demo",
    "lab-training": "Virtual lab training PF-15, virtual driver profiles",
    "mes-reference": "MES dispatch orders, BFF mes_listOrders/startFilling/completeFilling",
    "simulator-profiles": "Virtual driver profile catalog (meter, weighbridge)",
    "warehouse-app": "Warehouse reference solution",
    "licensed-driver-pack-pilot": "FW-50 licensed driver pack pilot",
}

FEATURE_INDEX = [
    {
        "id": "application-principles",
        "title": "Application creation principles",
        "description": "Canonical P1-P10 north star for solution developers and agents",
        "keywords": "application principles north star tree-first bundle declarative",
        "docRef": "APPLICATION_PRINCIPLES.md",
    },
    {
        "id": "agent-knowledge",
        "title": "Agent knowledge index",
        "description": "All application creation approaches and full doc map for internal agent",
        "keywords": "agent knowledge application create bundle tree-first operator",
        "docRef": "AGENT_KNOWLEDGE.md",
    },
    {
        "id": "platform-logic",
        "title": "Platform rules",
        "description": "BindingRule + dashboard context + onContextChange",
        "keywords": "platform rule context dashboard cel",
        "docRef": "PLATFORM_LOGIC.md",
    },
    {
        "id": "bindings",
        "title": "CEL bindings",
        "description": "Variable bindings, platform functions, binding rules",
        "keywords": "binding cel scale counterRate refAt",
        "docRef": "BINDINGS.md",
    },
    {
        "id": "solution-guide",
        "title": "Solution developer guide",
        "description": "Lifecycle: register, migrate, functions, bundle, operator UI",
        "keywords": "solution developer lifecycle terminal",
        "docRef": "SOLUTION_DEVELOPER_GUIDE.md",
    },
    {
        "id": "bff",
        "title": "BFF invoke",
        "description": "POST /api/v1/bff/invoke for application script functions",
        "keywords": "bff invoke function script",
        "docRef": "APPLICATIONS.md",
    },
    {
        "id": "workflows",
        "title": "BPMN workflows",
        "description": "workflows[] in bundle, publish_nats, fire_event tasks",
        "keywords": "workflow bpmn automation",
        "docRef": "WORKFLOWS.md",
    },
    {
        "id": "correlators",
        "title": "Correlators and alerts",
        "description": "alertRules + correlators react to variable thresholds",
        "keywords": "correlator alert rule threshold",
        "docRef": "AUTOMATION.md",
    },
    {
        "id": "federation",
        "title": "Federation bind",
        "description": "Overlay remote peer object on local path",
        "keywords": "federation peer bind proxy",
        "docRef": "FEDERATION.md",
    },
    {
        "id": "dashboards",
        "title": "Dashboards",
        "description": "DASHBOARD layout.widgets[], selectionKey, widget types",
        "keywords": "dashboard widget layout chart",
        "docRef": "DASHBOARDS.md",
    },
    {
        "id": "drivers",
        "title": "Device drivers",
        "description": "SNMP, Modbus, MQTT, virtual, OPC UA, …",
        "keywords": "driver snmp modbus virtual mqtt",
        "docRef": "DRIVERS.md",
    },
    {
        "id": "events",
        "title": "Event catalog",
        "description": "events[] in bundle, WS subscribe_events",
        "keywords": "event catalog subscribe websocket",
        "docRef": "MESSAGING.md",
    },
]


def read_text(path: Path, max_chars: int = 12000) -> str:
    if not path.exists():
        return ""
    text = path.read_text(encoding="utf-8")
    return text[:max_chars]


def platform_version() -> str:
    env = os.environ.get("ISPF_VERSION", "").strip()
    if env:
        return env
    try:
        result = subprocess.run(
            ["git", "describe", "--tags", "--abbrev=0"],
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=False,
        )
        if result.returncode == 0 and result.stdout.strip():
            return result.stdout.strip().lstrip("v")
    except OSError:
        pass
    props = ROOT / "gradle.properties"
    if props.exists():
        match = re.search(r"^version=(.+)$", props.read_text(encoding="utf-8"), re.M)
        if match:
            return match.group(1).strip()
    return "0.7.8"


def parse_driver_catalog(drivers_md: str) -> list[dict]:
    catalog: list[dict] = []
    lines = drivers_md.splitlines()
    start = -1
    for i, line in enumerate(lines):
        if "Полный список" in line and "DriverCatalog" in line:
            start = i
            break
    if start < 0:
        start = 0
    in_table = False
    for line in lines[start:]:
        stripped = line.strip()
        if stripped.startswith("| `driverId`"):
            in_table = True
            continue
        if not in_table:
            continue
        if not stripped.startswith("|"):
            if catalog:
                break
            continue
        if re.match(r"^\|\s*-+\s*\|", stripped):
            continue
        parts = [p.strip() for p in stripped.strip("|").split("|")]
        if len(parts) < 3:
            continue
        driver_id = parts[0].strip("`")
        if driver_id in {"", "driverId"}:
            continue
        catalog.append(
            {
                "driverId": driver_id,
                "module": parts[1].strip("`"),
                "description": parts[2],
                "keywords": f"{driver_id} {parts[2]}",
            }
        )
    return catalog


def load_examples() -> list[dict]:
    items: list[dict] = []
    if not EXAMPLES.exists():
        return items
    for bundle_path in sorted(EXAMPLES.glob("*/bundle.json")):
        folder = bundle_path.parent.name
        try:
            manifest = json.loads(bundle_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue
        sections = sorted(k for k, v in manifest.items() if v)
        items.append(
            {
                "appId": folder,
                "packageId": manifest.get("schemaName", folder).replace("app_", ""),
                "path": str(bundle_path.relative_to(ROOT)).replace("\\", "/"),
                "version": manifest.get("version"),
                "sections": sections,
                "manifest": manifest,
            }
        )
    return items


def build_example_summaries(examples: list[dict]) -> list[dict]:
    summaries: list[dict] = []
    for example in examples:
        app_id = str(example.get("appId", ""))
        sections = example.get("sections", [])
        key_sections = [s for s in sections if s in {
            "functions", "objects", "dashboards", "workflows", "migrations", "models", "events"
        }]
        summaries.append(
            {
                "appId": app_id,
                "purpose": EXAMPLE_PURPOSES.get(app_id, example.get("packageId", app_id)),
                "keySections": ", ".join(key_sections),
                "version": example.get("version"),
                "path": example.get("path"),
                "keywords": f"{app_id} {' '.join(key_sections)}",
            }
        )
    return summaries


def build_doc_chunks() -> list[dict]:
    slices = [
        ("application-principles", "Application creation principles P1-P10", "application-principles", DOCS / "APPLICATION_PRINCIPLES.md", 8000),
        ("agent-knowledge", "Agent application approaches", "agent-knowledge", DOCS / "AGENT_KNOWLEDGE.md", 16000),
        ("solution-developer", "Solution developer lifecycle", "solution", DOCS / "SOLUTION_DEVELOPER_GUIDE.md", 9000),
        ("public-api", "Public API", "all", DOCS / "SOLUTION_DEVELOPER_PUBLIC_API.md", 6000),
        ("applications", "Applications deploy", "applications", DOCS / "APPLICATIONS.md", 9000),
        ("platform-logic", "Platform rules dashboard context", "platform-logic", DOCS / "PLATFORM_LOGIC.md", 7000),
        ("bindings", "CEL bindings", "bindings", DOCS / "BINDINGS.md", 6000),
        ("object-functions", "Object tree functions", "functions", DOCS / "OBJECT_FUNCTIONS.md", 7000),
        ("models", "Object models", "models", DOCS / "MODELS.md", 6000),
        ("drivers", "Device drivers", "drivers", DOCS / "DRIVERS.md", 10000),
        ("workflows", "Workflows BPMN", "workflows", DOCS / "WORKFLOWS.md", 6000),
        ("automation", "Automation correlators", "automation", DOCS / "AUTOMATION.md", 5000),
        ("dashboards", "Dashboards widgets", "dashboards", DOCS / "DASHBOARDS.md", 8000),
        ("widgets", "Widget catalog", "widgets", DOCS / "WIDGETS.md", 8000),
        ("messaging", "Messaging events NATS", "features", DOCS / "MESSAGING.md", 5000),
        ("object-model", "Object tree model", "object-model", DOCS / "OBJECT_MODEL.md", 6000),
        ("ai-development", "AI agent ContextPack", "ai", DOCS / "AI_DEVELOPMENT.md", 7000),
        ("architecture", "Architecture principles", "architecture", DOCS / "ARCHITECTURE.md", 5000),
        ("web-console", "Web Console admin", "web-console", DOCS / "WEB_CONSOLE.md", 5000),
        ("reports", "SQL reports", "reports", DOCS / "REPORTS.md", 5000),
        ("lab-training", "Lab training exercises", "lab", DOCS / "LAB_TRAINING.md", 5000),
        ("mes-walkthrough", "MES reference walkthrough", "mes", DOCS / "REFERENCE_MES_WALKTHROUGH.md", 5000),
    ]
    chunks: list[dict] = []
    for chunk_id, title, topic, path, max_chars in slices:
        text = read_text(path, max_chars)
        if not text:
            continue
        keywords = f"{chunk_id} {title} {topic}"
        chunks.append(
            {
                "id": chunk_id,
                "title": title,
                "topic": topic,
                "keywords": keywords,
                "text": text,
            }
        )
    return chunks


def build_doc_catalog() -> list[dict]:
    """Index of all docs/*.md for agent routing (titles from first # heading)."""
    catalog: list[dict] = []
    for path in sorted(DOCS.glob("*.md")):
        text = read_text(path, 400)
        title = path.stem.replace("_", " ")
        for line in text.splitlines():
            if line.startswith("# "):
                title = line[2:].strip()
                break
        catalog.append(
            {
                "id": path.stem.lower(),
                "path": f"docs/{path.name}",
                "title": title,
                "keywords": f"{path.stem} {title}",
            }
        )
    for path in sorted((DOCS / "decisions").glob("*.md")):
        if path.name == "README.md":
            continue
        text = read_text(path, 300)
        title = path.stem
        for line in text.splitlines():
            if line.startswith("# "):
                title = line[2:].strip()
                break
        catalog.append(
            {
                "id": f"adr-{path.stem}",
                "path": f"docs/decisions/{path.name}",
                "title": title,
                "keywords": f"adr decision {path.stem} {title}",
            }
        )
    return catalog


def build_pack() -> dict:
    version = platform_version()
    examples = load_examples()
    drivers_doc = read_text(DOCS / "DRIVERS.md", 12000)
    pack = {
        "contextPackVersion": f"ispf-{version}",
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "platformVersion": version,
        "bundleManifest": {
            "fields": BUNDLE_MANIFEST_FIELDS,
            "rules": [
                "Output declarative bundle JSON only; no Java or React in platform main.",
                "Use operatorUi and dashboards[] for new operator screens.",
                "App SQL via migrations[]; never platform Flyway.",
                "Optional metadata may include generatedBy, promptId, contextPackVersion.",
            ],
        },
        "scriptSteps": SCRIPT_STEPS,
        "widgetTypes": WIDGET_TYPES,
        "featureIndex": FEATURE_INDEX,
        "driverCatalog": parse_driver_catalog(drivers_doc),
        "exampleSummaries": build_example_summaries(examples),
        "docChunks": build_doc_chunks(),
        "docCatalog": build_doc_catalog(),
        "apiSlice": {
            "applicationPrinciplesDoc": read_text(DOCS / "APPLICATION_PRINCIPLES.md", 8000),
            "agentKnowledgeDoc": read_text(DOCS / "AGENT_KNOWLEDGE.md", 12000),
            "solutionDeveloperDoc": read_text(DOCS / "SOLUTION_DEVELOPER_GUIDE.md", 8000),
            "platformLogicDoc": read_text(DOCS / "PLATFORM_LOGIC.md", 6000),
            "bindingsDoc": read_text(DOCS / "BINDINGS.md", 5000),
            "publicApiDoc": read_text(DOCS / "SOLUTION_DEVELOPER_PUBLIC_API.md", 8000),
            "applicationsDoc": read_text(DOCS / "APPLICATIONS.md", 8000),
            "messagingDoc": read_text(DOCS / "MESSAGING.md", 6000),
            "dashboardsDoc": read_text(DOCS / "DASHBOARDS.md", 6000),
            "driversDoc": drivers_doc[:8000],
            "workflowsDoc": read_text(DOCS / "WORKFLOWS.md", 6000),
            "automationDoc": read_text(DOCS / "AUTOMATION.md", 5000),
            "objectModelDoc": read_text(DOCS / "OBJECT_MODEL.md", 5000),
        },
        "examples": examples,
        "generationPolicy": {
            "allowedArtifacts": [
                "bundle",
                "migrations",
                "functions",
                "dashboards",
                "operatorUi",
                "events",
                "reports",
                "models",
                "workflows",
            ],
            "forbidden": [
                "java in ispf-server",
                "react in platform main",
                "platform flyway migrations for app tables",
                "custom bff routes",
            ],
        },
    }
    digest = hashlib.sha256(
        json.dumps(pack, sort_keys=True, ensure_ascii=False).encode("utf-8")
    ).hexdigest()
    pack["contentSha256"] = digest
    return pack


def main() -> None:
    pack = build_pack()
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    out_path = OUT_DIR / "ispf-context-pack.json"
    out_path.write_text(json.dumps(pack, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    SERVER_RESOURCE.parent.mkdir(parents=True, exist_ok=True)
    SERVER_RESOURCE.write_text(json.dumps(pack, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote {out_path}")
    print(f"Wrote {SERVER_RESOURCE}")
    print(f"contextPackVersion={pack['contextPackVersion']} drivers={len(pack['driverCatalog'])}")


if __name__ == "__main__":
    main()
