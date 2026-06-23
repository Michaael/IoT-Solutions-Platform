import { APPLICATIONS_ROOT } from "./createObjectMode";
import { OPERATOR_APPS_ROOT } from "./operatorAppsPath";
import { SECURITY_ROLES_ROOT } from "./securityRolePath";
import { SECURITY_USERS_ROOT } from "./securityUserPath";
import { ALERT_RULES_ROOT, CORRELATORS_ROOT } from "./automationPath";

/** Platform nodes seeded at bootstrap — not removable from the object tree. */
const NON_DELETABLE_PATHS = new Set([
  "root",
  "root.platform",
  "root.platform.devices",
  "root.platform.models",
  "root.platform.relative-models",
  "root.platform.instance-types",
  "root.platform.absolute-models",
  "root.platform.instances",
  "root.platform.dashboards",
  "root.platform.workflows",
  ALERT_RULES_ROOT,
  CORRELATORS_ROOT,
  APPLICATIONS_ROOT,
  OPERATOR_APPS_ROOT,
  "root.platform.security",
  SECURITY_USERS_ROOT,
  SECURITY_ROLES_ROOT,
]);

export function canDeleteObjectPath(path: string): boolean {
  return !NON_DELETABLE_PATHS.has(path);
}
