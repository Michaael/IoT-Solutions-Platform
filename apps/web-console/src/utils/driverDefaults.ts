import type { DriverMetadata } from "../types/drivers";

export function defaultDriverConfiguration(
  driver: DriverMetadata | undefined
): Record<string, string> {
  if (!driver?.configurationSchema) {
    return {};
  }
  return { ...driver.configurationSchema };
}

export function formatDriverConfigJson(driver: DriverMetadata | undefined): string {
  return JSON.stringify(defaultDriverConfiguration(driver), null, 2);
}
