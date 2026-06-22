#!/usr/bin/env python3
"""Build ISPF ContextPack for AI Development Layer (FW-41)."""

from __future__ import annotations

import hashlib
import json
import re
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
    "indicator",
    "chart",
    "sparkline",
    "progress",
    "gauge",
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


def read_text(path: Path, max_chars: int = 12000) -> str:
    if not path.exists():
        return ""
    text = path.read_text(encoding="utf-8")
    return text[:max_chars]


def load_examples() -> list[dict]:
    items: list[dict] = []
    if not EXAMPLES.exists():
        return items
    for bundle_path in sorted(EXAMPLES.glob("*/bundle.json")):
        app_id = bundle_path.parent.name
        try:
            manifest = json.loads(bundle_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue
        items.append(
            {
                "appId": manifest.get("displayName", app_id),
                "packageId": manifest.get("schemaName", app_id).replace("app_", ""),
                "path": str(bundle_path.relative_to(ROOT)).replace("\\", "/"),
                "version": manifest.get("version"),
                "sections": sorted(k for k, v in manifest.items() if v),
                "manifest": manifest,
            }
        )
    return items


def platform_version() -> str:
    props = ROOT / "gradle.properties"
    if props.exists():
        match = re.search(r"^version=(.+)$", props.read_text(encoding="utf-8"), re.M)
        if match:
            return match.group(1).strip()
    return "0.1.0-SNAPSHOT"


def build_pack() -> dict:
    version = platform_version()
    examples = load_examples()
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
        "apiSlice": {
            "publicApiDoc": read_text(DOCS / "SOLUTION_DEVELOPER_PUBLIC_API.md", 8000),
            "applicationsDoc": read_text(DOCS / "APPLICATIONS.md", 8000),
            "messagingDoc": read_text(DOCS / "MESSAGING.md", 6000),
            "dashboardsDoc": read_text(DOCS / "DASHBOARDS.md", 6000),
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


if __name__ == "__main__":
    main()
