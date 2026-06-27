#!/usr/bin/env python3
"""Fix root.platform.devices.test + spreadsheet dashboard for variable persist demo."""
from __future__ import annotations

import json
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = "https://ispf.iot-solutions.ru"
DEVICE_PATH = "root.platform.devices.test"
DASHBOARD_PATH = "root.platform.dashboards.spreadsheet"
VALUES_VARIABLE = "test"

SHEET_VALUES_SCHEMA = {
    "name": "sheetValues",
    "fields": [
        {
            "name": "rows",
            "type": "RECORD_LIST",
            "nestedSchema": {
                "name": "sheetCellRow",
                "fields": [
                    {"name": "cell", "type": "STRING"},
                    {"name": "value", "type": "STRING"},
                ],
            },
        }
    ],
}

EMPTY_SHEET_VALUE = {
    "schema": SHEET_VALUES_SCHEMA,
    "rows": [{"rows": []}],
}


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


def login() -> str:
    out = api("POST", "/api/v1/auth/login", {"username": "admin", "password": "admin"})
    token = out.get("token") if isinstance(out, dict) else None
    if not token:
        raise RuntimeError("login failed")
    return token


def recreate_test_variable(token: str) -> None:
    path = urllib.parse.quote(DEVICE_PATH, safe="")
    name = urllib.parse.quote(VALUES_VARIABLE, safe="")
    try:
        api("DELETE", f"/api/v1/objects/by-path/variables?path={path}&name={name}", token=token)
        print(f"deleted old variable {VALUES_VARIABLE}", file=sys.stderr)
    except RuntimeError as e:
        if "404" not in str(e):
            raise

    payload = {
        "name": VALUES_VARIABLE,
        "schema": SHEET_VALUES_SCHEMA,
        "readable": True,
        "writable": True,
        "initialValue": EMPTY_SHEET_VALUE,
        "historyEnabled": False,
        "historyRetentionDays": None,
    }
    api("POST", f"/api/v1/objects/by-path/variables?path={path}", payload, token=token)
    print(f"created {VALUES_VARIABLE} (RECORD_LIST, writable)", file=sys.stderr)


def fix_dashboard_widget(token: str) -> None:
    path = urllib.parse.quote(DASHBOARD_PATH, safe="")
    out = api("GET", f"/api/v1/dashboards/by-path?path={path}", token=token)
    layout = out.get("layout") if isinstance(out, dict) else None
    if not isinstance(layout, dict):
        layout_json = out.get("layoutJson") if isinstance(out, dict) else None
        layout = json.loads(layout_json) if isinstance(layout_json, str) else None
    if not isinstance(layout, dict):
        raise RuntimeError(f"unexpected layout: {out!r}")

    empty_config = json.dumps({"rows": 20, "cols": 8, "cells": {}}, ensure_ascii=False)
    changed = 0
    for widget in layout.get("widgets") or []:
        if widget.get("type") != "spreadsheet":
            continue
        fixes = {
            "objectPath": DEVICE_PATH,
            "valuesVariable": VALUES_VARIABLE,
            "persistMode": "variable",
            "editable": True,
            "modelHintPath": DEVICE_PATH,
            "sheetConfigJson": empty_config,
        }
        for key, value in fixes.items():
            if widget.get(key) != value:
                widget[key] = value
                changed += 1
        for key in ("sampleTemplate", "variableName", "contextPathKey", "sessionKey"):
            if key in widget:
                del widget[key]
                changed += 1
        print(
            f"widget {widget.get('id')!r}: objectPath={widget.get('objectPath')}, "
            f"valuesVariable={widget.get('valuesVariable')}, empty cells",
            file=sys.stderr,
        )

    if changed == 0:
        print("dashboard widget already correct")
        return

    api(
        "PUT",
        f"/api/v1/dashboards/by-path/layout?path={path}",
        {"layoutJson": json.dumps(layout, ensure_ascii=False)},
        token=token,
    )
    print(f"saved dashboard layout ({changed} field(s))")


def main() -> int:
    token = login()
    recreate_test_variable(token)
    fix_dashboard_widget(token)
    print("OK — open dashboard, edit cells, reload; variable test should persist")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
