#!/usr/bin/env python3
"""Create platform-metrics-probe device + dashboard; sync GET /api/v1/platform/metrics to variables."""

from __future__ import annotations

import argparse
import json
import sys
import threading
import time
from urllib.parse import quote

import requests

DEVICE_PATH = "root.platform.devices.platform-metrics-probe"
DASHBOARD_PATH = "root.platform.dashboards.platform-metrics"

def int_schema(name: str) -> dict:
    return {"name": name, "fields": [{"name": "value", "type": "INTEGER"}]}


def double_schema(name: str) -> dict:
    return {"name": name, "fields": [{"name": "value", "type": "DOUBLE"}]}


METRIC_VARS = [
    ("eventHistoryRecords", "INTEGER", True),
    ("eventsPerSecond", "DOUBLE", False),
    ("alertFiresTotal", "INTEGER", True),
    ("alertFiresPerSecond", "DOUBLE", False),
    ("objectChangeQueueSize", "INTEGER", False),
    ("heapUsedMb", "DOUBLE", False),
    ("activeConnections", "INTEGER", False),
    ("threadsAwaitingConnection", "INTEGER", False),
    ("activeDrivers", "INTEGER", False),
    ("workflowInstancesRunning", "INTEGER", False),
    ("variableHistorySamples", "INTEGER", False),
]


class Client:
    def __init__(self, base_url: str, host_header: str | None, timeout: float):
        self.base = base_url.rstrip("/")
        self.timeout = timeout
        self.session = requests.Session()
        self.session.headers.update({"Accept": "application/json"})
        if host_header:
            self.session.headers["Host"] = host_header
        self.token: str | None = None

    def login(self, username: str, password: str) -> None:
        r = self.session.post(
            f"{self.base}/api/v1/auth/login",
            json={"username": username, "password": password},
            timeout=self.timeout,
        )
        r.raise_for_status()
        self.token = r.json()["token"]
        self.session.headers["Authorization"] = f"Bearer {self.token}"

    def request(self, method: str, path: str, **kwargs) -> requests.Response:
        kwargs.setdefault("timeout", self.timeout)
        return self.session.request(method, f"{self.base}{path}", **kwargs)


def section_values(metrics: dict, section_id: str) -> dict:
    for section in metrics.get("sections", []):
        if section.get("id") == section_id:
            return section.get("values") or {}
    return {}


def build_dashboard_layout() -> str:
    probe = DEVICE_PATH
    widgets = [
        {
            "id": "events-total",
            "type": "value",
            "title": "Event journal records",
            "x": 0, "y": 0, "w": 3, "h": 2,
            "objectPath": probe,
            "variableName": "eventHistoryRecords",
            "valueField": "value",
            "decimals": 0,
        },
        {
            "id": "events-rate",
            "type": "gauge",
            "title": "Events / s",
            "x": 3, "y": 0, "w": 3, "h": 2,
            "objectPath": probe,
            "variableName": "eventsPerSecond",
            "valueField": "value",
            "min": 0,
            "max": 500,
            "decimals": 1,
        },
        {
            "id": "alert-rate",
            "type": "gauge",
            "title": "Alert fires / s",
            "x": 6, "y": 0, "w": 3, "h": 2,
            "objectPath": probe,
            "variableName": "alertFiresPerSecond",
            "valueField": "value",
            "min": 0,
            "max": 500,
            "decimals": 1,
        },
        {
            "id": "heap",
            "type": "value",
            "title": "Heap used (MB)",
            "x": 9, "y": 0, "w": 3, "h": 2,
            "objectPath": probe,
            "variableName": "heapUsedMb",
            "valueField": "value",
            "decimals": 1,
            "unit": "MB",
        },
        {
            "id": "db-active",
            "type": "value",
            "title": "DB active connections",
            "x": 0, "y": 2, "w": 3, "h": 2,
            "objectPath": probe,
            "variableName": "activeConnections",
            "valueField": "value",
            "decimals": 0,
        },
        {
            "id": "oc-queue",
            "type": "value",
            "title": "Object-change queue",
            "x": 3, "y": 2, "w": 3, "h": 2,
            "objectPath": probe,
            "variableName": "objectChangeQueueSize",
            "valueField": "value",
            "decimals": 0,
        },
        {
            "id": "drivers",
            "type": "value",
            "title": "Active drivers",
            "x": 6, "y": 2, "w": 3, "h": 2,
            "objectPath": probe,
            "variableName": "activeDrivers",
            "valueField": "value",
            "decimals": 0,
        },
        {
            "id": "workflows",
            "type": "value",
            "title": "Workflows running",
            "x": 9, "y": 2, "w": 3, "h": 2,
            "objectPath": probe,
            "variableName": "workflowInstancesRunning",
            "valueField": "value",
            "decimals": 0,
        },
        {
            "id": "db-wait",
            "type": "indicator",
            "title": "DB pool pressure",
            "x": 0, "y": 4, "w": 3, "h": 2,
            "objectPath": probe,
            "variableName": "threadsAwaitingConnection",
            "valueField": "value",
            "trueLabel": "Waiting",
            "falseLabel": "OK",
        },
        {
            "id": "history-samples",
            "type": "value",
            "title": "Variable history samples",
            "x": 3, "y": 4, "w": 3, "h": 2,
            "objectPath": probe,
            "variableName": "variableHistorySamples",
            "valueField": "value",
            "decimals": 0,
        },
        {
            "id": "alert-total",
            "type": "value",
            "title": "Alert fires (total)",
            "x": 6, "y": 4, "w": 3, "h": 2,
            "objectPath": probe,
            "variableName": "alertFiresTotal",
            "valueField": "value",
            "decimals": 0,
        },
        {
            "id": "events-chart",
            "type": "chart",
            "title": "Event journal growth",
            "x": 0, "y": 6, "w": 12, "h": 4,
            "objectPath": probe,
            "variableName": "eventHistoryRecords",
            "valueField": "value",
            "chartStyle": "area",
            "maxPoints": 120,
            "historyRange": "live",
            "refreshIntervalMs": 5000,
        },
    ]
    return json.dumps({"columns": 12, "rowHeight": 72, "widgets": widgets}, separators=(",", ":"))


def ensure_device(client: Client) -> None:
    r = client.request(
        "POST",
        "/api/v1/objects",
        json={
            "parentPath": "root.platform.devices",
            "name": "platform-metrics-probe",
            "type": "DEVICE",
            "displayName": "Platform Metrics Probe",
            "description": "Mirrors GET /api/v1/platform/metrics for dashboard monitoring during load tests.",
        },
    )
    if r.status_code not in (200, 201, 409):
        r.raise_for_status()


def schema_for(name: str, field_type: str) -> dict:
    if field_type == "DOUBLE":
        return double_schema(name)
    return int_schema(name)


def ensure_variables(client: Client) -> None:
    for name, field_type, history in METRIC_VARS:
        schema = schema_for(name, field_type)
        body: dict = {
            "name": name,
            "schema": schema,
            "readable": True,
            "writable": True,
            "historyEnabled": history,
        }
        if history:
            body["historyRetentionDays"] = 1
        cr = client.request(
            "POST",
            f"/api/v1/objects/by-path/variables?path={quote(DEVICE_PATH, safe='')}",
            json=body,
        )
        if cr.status_code in (200, 201, 409):
            continue
        if cr.status_code == 400 and "already exists" in cr.text.lower():
            continue
        print(f"  WARN variable {name}: HTTP {cr.status_code} {cr.text[:120]}")


def ensure_dashboard(client: Client) -> None:
    r = client.request(
        "POST",
        "/api/v1/objects",
        json={
            "parentPath": "root.platform.dashboards",
            "name": "platform-metrics",
            "type": "DASHBOARD",
            "displayName": "Platform Metrics",
            "description": "Live view of /api/v1/platform/metrics (automation, runtime, DB, drivers).",
            "templateId": "dashboard-v1",
        },
    )
    if r.status_code not in (200, 201, 409):
        r.raise_for_status()
    layout = build_dashboard_layout()
    lr = client.request(
        "PUT",
        f"/api/v1/dashboards/by-path/layout?path={quote(DASHBOARD_PATH, safe='')}",
        json={"layoutJson": layout},
    )
    lr.raise_for_status()
    client.request(
        "PUT",
        f"/api/v1/dashboards/by-path/title?path={quote(DASHBOARD_PATH, safe='')}",
        json={"title": "Platform Metrics"},
    )


def put_value(client: Client, name: str, field_type: str, value: float | int) -> None:
    client.request(
        "PUT",
        f"/api/v1/objects/by-path/variables?path={quote(DEVICE_PATH, safe='')}&name={name}",
        json={"schema": schema_for(name, field_type), "rows": [{"value": value}]},
    )


class MetricsSyncer:
    def __init__(self, client: Client, interval_sec: float = 5.0):
        self.client = client
        self.interval_sec = interval_sec
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        self._last_events: int | None = None
        self._last_alert_fires: int | None = None
        self._last_ts: float | None = None

    def sync_once(self) -> dict:
        r = self.client.request("GET", "/api/v1/platform/metrics")
        r.raise_for_status()
        data = r.json()
        runtime = section_values(data, "runtime")
        database = section_values(data, "database")
        drivers = section_values(data, "drivers")
        automation = section_values(data, "automation")
        history = section_values(data, "variableHistory")

        events = int(automation.get("eventHistoryRecords") or 0)
        alert_fires = int(automation.get("alertFiresTotal") or 0)
        now = time.perf_counter()
        eps = 0.0
        alert_eps = 0.0
        if self._last_events is not None and self._last_ts is not None:
            dt = now - self._last_ts
            if dt > 0:
                eps = max(0.0, (events - self._last_events) / dt)
                if self._last_alert_fires is not None:
                    alert_eps = max(0.0, (alert_fires - self._last_alert_fires) / dt)
        self._last_events = events
        self._last_alert_fires = alert_fires
        self._last_ts = now

        put_value(self.client, "eventHistoryRecords", "INTEGER", events)
        put_value(self.client, "eventsPerSecond", "DOUBLE", round(eps, 2))
        put_value(self.client, "alertFiresTotal", "INTEGER", alert_fires)
        put_value(self.client, "alertFiresPerSecond", "DOUBLE", round(alert_eps, 2))
        put_value(
            self.client,
            "objectChangeQueueSize",
            "INTEGER",
            int(automation.get("objectChangeQueueSize") or 0),
        )
        put_value(self.client, "heapUsedMb", "DOUBLE", float(runtime.get("heapUsedMb") or 0))
        put_value(self.client, "activeConnections", "INTEGER", int(database.get("activeConnections") or 0))
        put_value(
            self.client,
            "threadsAwaitingConnection",
            "INTEGER",
            int(database.get("threadsAwaitingConnection") or 0),
        )
        put_value(self.client, "activeDrivers", "INTEGER", int(drivers.get("activeDrivers") or 0))
        put_value(
            self.client,
            "workflowInstancesRunning",
            "INTEGER",
            int(automation.get("workflowInstancesRunning") or 0),
        )
        put_value(self.client, "variableHistorySamples", "INTEGER", int(history.get("sampleCount") or 0))
        return {
            "eventsPerSecond": eps,
            "alertFiresPerSecond": alert_eps,
            "eventHistoryRecords": events,
        }

    def start(self) -> None:
        if self._thread and self._thread.is_alive():
            return

        def loop() -> None:
            while not self._stop.wait(self.interval_sec):
                try:
                    self.sync_once()
                except Exception as exc:
                    print(f"  metrics sync error: {exc}", file=sys.stderr)

        self._thread = threading.Thread(target=loop, name="metrics-sync", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        if self._thread:
            self._thread.join(timeout=self.interval_sec + 2)


def setup(client: Client) -> None:
    ensure_device(client)
    ensure_variables(client)
    ensure_dashboard(client)
    print(f"Device:  {DEVICE_PATH}")
    print(f"Dashboard: {DASHBOARD_PATH}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Setup platform metrics monitor device + dashboard")
    parser.add_argument("--base-url", default="https://ispf.iot-solutions.ru")
    parser.add_argument("--host-header", default="")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin")
    parser.add_argument("--sync-seconds", type=float, default=0, help="Run background sync for N seconds (0=setup only)")
    parser.add_argument("--interval", type=float, default=5.0)
    parser.add_argument("--timeout", type=float, default=30.0)
    args = parser.parse_args()

    client = Client(args.base_url, args.host_header or None, args.timeout)
    client.login(args.username, args.password)
    setup(client)

    if args.sync_seconds > 0:
        syncer = MetricsSyncer(client, args.interval)
        syncer.sync_once()
        print(f"Initial sync: events={syncer._last_events}")
        syncer.start()
        print(f"Syncing every {args.interval}s for {args.sync_seconds}s...")
        time.sleep(args.sync_seconds)
        snap = syncer.sync_once()
        syncer.stop()
        print(f"Final: events={snap['eventHistoryRecords']} rate={snap['eventsPerSecond']:.2f}/s")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
