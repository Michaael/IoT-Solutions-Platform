#!/usr/bin/env python3
"""Fix root.platform.dashboards.spreadsheet widget binding on prod/local ISPF."""
from __future__ import annotations

import json
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = "http://127.0.0.1:8080"
DASHBOARD_PATH = "root.platform.dashboards.spreadsheet"
DEVICE_PATH = "root.platform.devices.lab-userA-01"
VALUES_VARIABLE = "sheetValues"


def api(method: str, path: str, body: dict | None = None, token: str | None = None) -> object:
    headers = {"Content-Type": "application/json", "X-ISPF-Role": "admin"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(f"{BASE}{path}", data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {path} -> {e.code}: {detail}") from e


def login() -> str | None:
    try:
        out = api("POST", "/api/v1/auth/login", {"username": "admin", "password": "admin"})
        token = out.get("token") if isinstance(out, dict) else None
        return token or None
    except RuntimeError:
        return None


def get_layout(token: str) -> dict:
    path = urllib.parse.quote(DASHBOARD_PATH, safe="")
    out = api("GET", f"/api/v1/dashboards/by-path?path={path}", token=token)
    if isinstance(out, dict):
        layout = out.get("layout")
        if isinstance(layout, dict):
            return layout
        layout_json = out.get("layoutJson")
        if isinstance(layout_json, str) and layout_json.strip():
            return json.loads(layout_json)
    raise RuntimeError(f"unexpected layout response: {out!r}")


def fix_widget(widget: dict) -> bool:
    if widget.get("type") != "spreadsheet":
        return False
    changed = False
    fixes = {
        "objectPath": DEVICE_PATH,
        "valuesVariable": VALUES_VARIABLE,
        "modelHintPath": DEVICE_PATH,
        "contextPathKey": None,
        "variableName": None,
        "valueField": None,
        "sampleTemplate": None,
        "sessionKey": None,
    }
    for key, value in fixes.items():
        if value is None:
            if key in widget:
                del widget[key]
                changed = True
        elif widget.get(key) != value:
            widget[key] = value
            changed = True
    if widget.get("persistMode") != "variable":
        widget["persistMode"] = "variable"
        changed = True
    if not widget.get("editable"):
        widget["editable"] = True
        changed = True
    return changed


def main() -> int:
    token = login()
    layout = get_layout(token)
    widgets = layout.get("widgets") or []
    fixed = 0
    for w in widgets:
        if fix_widget(w):
            fixed += 1
            print(
                f"fixed widget {w.get('id')!r}: "
                f"objectPath={w.get('objectPath')}, valuesVariable={w.get('valuesVariable')}",
                file=sys.stderr,
            )
    if fixed == 0:
        print("no spreadsheet widgets changed (already correct?)")
        return 0

    path = urllib.parse.quote(DASHBOARD_PATH, safe="")
    api(
        "PUT",
        f"/api/v1/dashboards/by-path/layout?path={path}",
        {"layoutJson": json.dumps(layout, ensure_ascii=False)},
        token=token,
    )
    print(f"saved layout for {DASHBOARD_PATH} ({fixed} widget(s))")
    print(json.dumps(layout, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
