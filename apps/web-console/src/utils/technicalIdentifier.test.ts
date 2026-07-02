import { describe, expect, it } from "vitest";
import { isTechnicalIdentifier } from "./technicalIdentifier";

describe("isTechnicalIdentifier", () => {
  it("accepts valid code identifiers", () => {
    expect(isTechnicalIdentifier("create_alarm_2", "code")).toBe(true);
    expect(isTechnicalIdentifier("_internal", "code")).toBe(true);
  });

  it.each(["функция", "alarm Сreate", "2function", "with-hyphen", " name "])(
    "rejects invalid code identifier %s",
    (value) => expect(isTechnicalIdentifier(value, "code")).toBe(false)
  );

  it("validates path segments without allowing Cyrillic or whitespace", () => {
    expect(isTechnicalIdentifier("device-01", "pathSegment")).toBe(true);
    expect(isTechnicalIdentifier("устройство", "pathSegment")).toBe(false);
    expect(isTechnicalIdentifier("device 01", "pathSegment")).toBe(false);
  });

  it("keeps the existing dotted model and security name formats", () => {
    expect(isTechnicalIdentifier("sensor.v2", "dottedName")).toBe(true);
    expect(isTechnicalIdentifier("operator.2", "securityName")).toBe(true);
    expect(isTechnicalIdentifier("x", "securityName")).toBe(false);
    expect(isTechnicalIdentifier("модель.v2", "dottedName")).toBe(false);
  });
});
