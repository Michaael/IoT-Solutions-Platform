import { describe, expect, it } from "vitest";
import {
  activatorsSummary,
  buildRemoteVariableChange,
  CUSTOM_BINDING_EVENT,
  defaultBindingActivators,
  patchBindingActivators,
  remoteActivatorRef,
  resolveOnEventAfterSelect,
  resolveOnEventSelectValue,
} from "./bindingActivatorsUtils";

describe("defaultBindingActivators", () => {
  it("starts with self wildcard and no timers", () => {
    const activators = defaultBindingActivators();
    expect(activators.onStartup).toBe(false);
    expect(activators.onVariableChange).toEqual([{ objectPath: "self", variableName: "*" }]);
    expect(activators.onEvent).toBeNull();
    expect(activators.periodicMs).toBe(0);
  });
});

describe("resolveOnEventSelectValue", () => {
  it("returns empty when no event is configured", () => {
    expect(resolveOnEventSelectValue(defaultBindingActivators(), ["alarmRaised"])).toBe("");
  });

  it("returns known event name when it is in the catalog", () => {
    const activators = patchBindingActivators(defaultBindingActivators(), { onEvent: "alarmRaised" });
    expect(resolveOnEventSelectValue(activators, ["alarmRaised", "valueChanged"])).toBe("alarmRaised");
  });

  it("falls back to custom option for unknown event names", () => {
    const activators = patchBindingActivators(defaultBindingActivators(), { onEvent: "legacyEvent" });
    expect(resolveOnEventSelectValue(activators, ["alarmRaised"])).toBe(CUSTOM_BINDING_EVENT);
  });
});

describe("resolveOnEventAfterSelect", () => {
  it("clears event when none is selected", () => {
    expect(resolveOnEventAfterSelect("alarmRaised", "", ["alarmRaised"])).toBeNull();
  });

  it("keeps custom event text when switching to custom mode", () => {
    expect(resolveOnEventAfterSelect("legacyEvent", CUSTOM_BINDING_EVENT, ["alarmRaised"])).toBe("legacyEvent");
  });

  it("seeds empty custom event when catalog event was selected", () => {
    expect(resolveOnEventAfterSelect("alarmRaised", CUSTOM_BINDING_EVENT, ["alarmRaised"])).toBe("");
  });

  it("stores catalog event directly", () => {
    expect(resolveOnEventAfterSelect(null, "valueChanged", ["valueChanged"])).toBe("valueChanged");
  });
});

describe("buildRemoteVariableChange", () => {
  it("resets to self wildcard when path is blank", () => {
    expect(buildRemoteVariableChange("  ", "temperature")).toEqual([
      { objectPath: "self", variableName: "*" },
    ]);
  });

  it("trims path and defaults variable to wildcard", () => {
    expect(buildRemoteVariableChange(" root.platform.devices.foo ", "  ")).toEqual([
      { objectPath: "root.platform.devices.foo", variableName: "*" },
    ]);
  });

  it("keeps explicit remote variable name", () => {
    expect(buildRemoteVariableChange("root.a", "temperature")).toEqual([
      { objectPath: "root.a", variableName: "temperature" },
    ]);
  });
});

describe("remoteActivatorRef", () => {
  it("finds non-self variable ref", () => {
    const activators = patchBindingActivators(defaultBindingActivators(), {
      onVariableChange: [{ objectPath: "root.device", variableName: "status" }],
    });
    expect(remoteActivatorRef(activators)).toEqual({
      objectPath: "root.device",
      variableName: "status",
    });
  });

  it("returns undefined for self-only activators", () => {
    expect(remoteActivatorRef(defaultBindingActivators())).toBeUndefined();
  });
});

describe("activatorsSummary", () => {
  it("joins startup, remote refs, events, and periodic timers", () => {
    const summary = activatorsSummary({
      activators: {
        onStartup: true,
        onVariableChange: [
          { objectPath: "self", variableName: "*" },
          { objectPath: "root.device", variableName: "temperature" },
        ],
        onEvent: "alarmRaised",
        periodicMs: 5000,
      },
    });
    expect(summary).toBe("startup, self:*, root.device:temperature, event:alarmRaised, 5000ms");
  });

  it("shows self wildcard for default activators", () => {
    expect(activatorsSummary({ activators: defaultBindingActivators() })).toBe("self:*");
  });

  it("shows em dash when variable-change list is empty", () => {
    expect(activatorsSummary({
      activators: {
        onStartup: false,
        onVariableChange: [],
        onEvent: null,
        periodicMs: 0,
      },
    })).toBe("—");
  });

  it("includes context-change activator", () => {
    expect(activatorsSummary({
      activators: {
        onStartup: false,
        onVariableChange: [],
        onEvent: null,
        periodicMs: 0,
        onContextChange: true,
      },
    })).toBe("context");
  });
});

describe("patchBindingActivators", () => {
  it("merges partial updates without mutating the source", () => {
    const base = defaultBindingActivators();
    const next = patchBindingActivators(base, { onStartup: true, periodicMs: 1000 });
    expect(next).toEqual({
      onStartup: true,
      onVariableChange: [{ objectPath: "self", variableName: "*" }],
      onEvent: null,
      periodicMs: 1000,
    });
    expect(base.onStartup).toBe(false);
  });
});
