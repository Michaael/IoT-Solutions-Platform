import { describe, expect, it } from "vitest";
import type { ObjectEvent } from "../types/event";
import type { WorkQueueItem } from "../types/operator";
import type { OperatorUi } from "../types/operatorUi";
import {
  eventMatchesOperatorApp,
  filterOperatorSidebarEvents,
  filterOperatorSidebarTasks,
  isAlarmLevel,
  taskMatchesOperatorApp,
} from "./operatorSidebarScope";

function event(partial: Partial<ObjectEvent> & Pick<ObjectEvent, "eventName" | "level">): ObjectEvent {
  return {
    id: partial.id ?? "1",
    objectPath: partial.objectPath ?? "root.platform.devices.demo-sensor-01",
    eventName: partial.eventName,
    level: partial.level,
    payload: partial.payload ?? { schema: {}, rows: [] },
    timestamp: partial.timestamp ?? "2026-06-23T12:00:00Z",
  };
}

function task(partial: Partial<WorkQueueItem> & Pick<WorkQueueItem, "workflowPath">): WorkQueueItem {
  return {
    id: partial.id ?? "t1",
    instanceId: partial.instanceId ?? "i1",
    workflowPath: partial.workflowPath,
    operatorAppId: partial.operatorAppId ?? null,
    taskNodeId: partial.taskNodeId ?? "n1",
    title: partial.title ?? "Task",
    instructions: partial.instructions ?? "",
    assigneeRole: partial.assigneeRole ?? "operator",
    status: partial.status ?? "OPEN",
    assignee: partial.assignee ?? null,
    createdAt: partial.createdAt ?? "2026-06-23T12:00:00Z",
    claimedAt: partial.claimedAt ?? null,
    completedAt: partial.completedAt ?? null,
  };
}

describe("operatorSidebarScope", () => {
  it("detects alarm levels", () => {
    expect(isAlarmLevel("WARNING")).toBe(true);
    expect(isAlarmLevel("INFO")).toBe(false);
  });

  it("matches operator app events by alarm bar rules", () => {
    const ui: OperatorUi = {
      appId: "mini-tec",
      title: "Mini TEC",
      defaultDashboard: "",
      dashboards: [],
      alarmBar: {
        enabled: true,
        minLevel: "WARNING",
        rules: [
          {
            id: "gpu",
            eventNames: ["gpuProtOverload"],
            objectPathPrefix: "root.platform.devices.mini-tec-plant.gpu-01",
            minLevel: "WARNING",
          },
        ],
      },
    };
    expect(eventMatchesOperatorApp(event({ eventName: "thresholdExceeded", level: "WARNING" }), ui)).toBe(
      false
    );
    expect(
      eventMatchesOperatorApp(
        event({
          eventName: "gpuProtOverload",
          level: "WARNING",
          objectPath: "root.platform.devices.mini-tec-plant.gpu-01",
        }),
        ui
      )
    ).toBe(true);
  });

  it("assigns shared-path alarms to the operator app with alarm bar rules", () => {
    const mesReference: OperatorUi = {
      appId: "mes-reference",
      title: "MES",
      defaultDashboard: "",
      dashboards: [],
      eventJournalObjectPath: "root.platform.devices.demo-sensor-01",
    };
    const platform: OperatorUi = {
      appId: "platform",
      title: "Platform",
      defaultDashboard: "",
      dashboards: [],
      alarmBar: {
        enabled: true,
        minLevel: "WARNING",
        rules: [
          {
            id: "demo",
            eventNames: ["thresholdExceeded"],
            objectPathPrefix: "root.platform.devices.demo-sensor-01",
            minLevel: "WARNING",
          },
        ],
      },
    };
    const items = filterOperatorSidebarEvents(
      [event({ eventName: "thresholdExceeded", level: "WARNING" })],
      {
        appId: "mes-reference",
        ui: mesReference,
        operatorApps: [mesReference, platform],
      }
    );
    expect(items).toHaveLength(0);

    const platformItems = filterOperatorSidebarEvents(
      [event({ eventName: "thresholdExceeded", level: "WARNING" })],
      {
        appId: "platform",
        ui: platform,
        operatorApps: [mesReference, platform],
      }
    );
    expect(platformItems).toHaveLength(1);
  });

  it("matches tasks by workflow.operatorAppId", () => {
    const platform: OperatorUi = {
      appId: "platform",
      title: "Platform",
      defaultDashboard: "",
      dashboards: [],
    };
    expect(
      taskMatchesOperatorApp(
        task({ workflowPath: "root.platform.workflows.demo", operatorAppId: "platform" }),
        "platform",
        platform
      )
    ).toBe(true);
    expect(
      taskMatchesOperatorApp(
        task({ workflowPath: "root.platform.workflows.demo", operatorAppId: "mes-reference" }),
        "platform",
        platform
      )
    ).toBe(false);
  });

  it("filters sidebar tasks by operatorAppId", () => {
    const platform: OperatorUi = {
      appId: "platform",
      title: "Platform",
      defaultDashboard: "",
      dashboards: [],
    };
    const mesReference: OperatorUi = {
      appId: "mes-reference",
      title: "MES",
      defaultDashboard: "",
      dashboards: [],
    };
    const platformTasks = filterOperatorSidebarTasks(
      [
        task({ workflowPath: "root.platform.workflows.demo-alarm-handler", operatorAppId: "platform" }),
        task({ workflowPath: "root.platform.workflows.other", operatorAppId: "mes-reference" }),
      ],
      { appId: "platform", ui: platform, operatorApps: [platform, mesReference] }
    );
    expect(platformTasks).toHaveLength(1);
    expect(platformTasks[0]?.operatorAppId).toBe("platform");
  });

  it("falls back to workflow path prefix when operatorAppId is missing", () => {
    const platform: OperatorUi = {
      appId: "platform",
      title: "Platform",
      defaultDashboard: "",
      dashboards: [],
    };
    const mesReference: OperatorUi = {
      appId: "mes-reference",
      title: "MES",
      defaultDashboard: "",
      dashboards: [],
      workQueueWorkflowPathPrefix: "root.platform.applications.mes-reference.workflows",
    };
    const platformTasks = filterOperatorSidebarTasks(
      [
        task({ workflowPath: "root.platform.workflows.demo-alarm-handler" }),
        task({ workflowPath: "root.platform.applications.mes-reference.workflows.ack" }),
      ],
      { appId: "platform", ui: platform, operatorApps: [platform, mesReference] }
    );
    expect(platformTasks).toHaveLength(1);
    expect(platformTasks[0]?.workflowPath).toContain("root.platform.workflows");
  });
});
