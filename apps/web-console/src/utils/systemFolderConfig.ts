import { MODELS_ROOT } from "../types/models";
import type { ObjectType } from "../types";
import { isOperatorAppChildPath } from "./operatorAppsPath";
import { isSecurityRolePath, isSecurityRolesRoot } from "./securityRolePath";
import { isSecurityUserPath, isSecurityUsersRoot } from "./securityUserPath";
import {
  isAlertRulePath,
  isAlertRulesRoot,
  isCorrelatorPath,
  isCorrelatorsRoot,
} from "./automationPath";
import { isFederationRoot } from "./federationPath";
import { isTenantsRoot } from "./tenantPath";

const APPLICATION_SUBFOLDER_SUFFIXES = [
  ".functions",
  ".reports",
  ".schedules",
  ".bindings",
  ".migrations",
  ".screens",
] as const;

const CATALOG_CONTAINER_TYPES: ReadonlySet<ObjectType> = new Set([
  "PLATFORM",
  "DEVICES",
  "DASHBOARDS",
  "WORKFLOWS",
  "ALERT_RULES",
  "CORRELATORS",
  "APPLICATIONS",
  "OPERATOR_APPS",
  "SECURITY",
  "FUNCTIONS",
  "REPORTS",
  "SCHEDULES",
  "BINDINGS",
  "MIGRATIONS",
  "SCREENS",
]);

const EXACT_CATALOG_PATHS: ReadonlySet<string> = new Set([
  "root.platform",
  "root.platform.devices",
  MODELS_ROOT,
  "root.platform.dashboards",
  "root.platform.reports",
  "root.platform.workflows",
  "root.platform.alert-rules",
  "root.platform.correlators",
  "root.platform.applications",
  "root.platform.operator-apps",
  "root.platform.security",
]);

export interface SystemFolderListMeta {
  title: string;
  description: string;
  idColumnLabel: string;
}

const META_BY_TYPE: Partial<Record<ObjectType, SystemFolderListMeta>> = {
  PLATFORM: {
    title: "Платформа",
    description:
      "Корневые разделы платформы. Выберите объект в дереве или в списке — свойства редактируются в инспекторе.",
    idColumnLabel: "Раздел",
  },
  DEVICES: {
    title: "Устройства",
    description:
      "Подключённые устройства в root.platform.devices. Выберите устройство в дереве или в списке.",
    idColumnLabel: "ID",
  },
  DASHBOARDS: {
    title: "Дашборды",
    description:
      "HMI-дашборды в root.platform.dashboards. Двойной щелчок в дереве, «Открыть» в списке или кнопка «Открыть в редакторе».",
    idColumnLabel: "ID",
  },
  WORKFLOWS: {
    title: "Workflow",
    description: "BPMN-процессы в root.platform.workflows.",
    idColumnLabel: "ID",
  },
  ALERT_RULES: {
    title: "Правила алертов",
    description:
      "CEL-правила в root.platform.alert-rules публикуют события при изменении переменных.",
    idColumnLabel: "ID",
  },
  CORRELATORS: {
    title: "Корреляторы",
    description:
      "Корреляторы в root.platform.correlators реагируют на события и запускают workflow.",
    idColumnLabel: "ID",
  },
  APPLICATIONS: {
    title: "Приложения",
    description:
      "Deploy-приложения (функции, отчёты, bundle) в root.platform.applications.",
    idColumnLabel: "App ID",
  },
  OPERATOR_APPS: {
    title: "Operator Apps",
    description:
      "Operator UI в root.platform.operator-apps — набор дашбордов для ?mode=operator&app=<id>.",
    idColumnLabel: "App ID",
  },
  SECURITY: {
    title: "Безопасность",
    description: "Разделы безопасности платформы в root.platform.security.",
    idColumnLabel: "Раздел",
  },
  FUNCTIONS: {
    title: "Функции",
    description: "Функции приложения, вызываемые через API и binding.",
    idColumnLabel: "ID",
  },
  REPORTS: {
    title: "Отчёты",
    description:
      "SQL-отчёты в root.platform.reports. Двойной щелчок в дереве, «Открыть» в списке или кнопка «Открыть в редакторе».",
    idColumnLabel: "ID",
  },
  SCHEDULES: {
    title: "Расписания",
    description: "Планировщики приложения.",
    idColumnLabel: "ID",
  },
  BINDINGS: {
    title: "Привязки",
    description: "SQL-привязки переменных приложения.",
    idColumnLabel: "ID",
  },
  MIGRATIONS: {
    title: "Миграции",
    description: "SQL-миграции схемы приложения.",
    idColumnLabel: "ID",
  },
  SCREENS: {
    title: "Экраны",
    description: "Экраны operator-приложения.",
    idColumnLabel: "ID",
  },
};

const MODELS_FOLDER_META: SystemFolderListMeta = {
  title: "Модели",
  description:
    "Определения моделей в root.platform.models. Полное определение — в редакторе (двойной щелчок).",
  idColumnLabel: "ID",
};

function isApplicationSubfolder(path: string): boolean {
  return APPLICATION_SUBFOLDER_SUFFIXES.some((suffix) => path.endsWith(suffix));
}

function resolveCatalogType(path: string, objectType?: ObjectType): ObjectType | null {
  if (path === MODELS_ROOT) {
    return "MODEL";
  }
  if (objectType && CATALOG_CONTAINER_TYPES.has(objectType)) {
    return objectType;
  }
  if (EXACT_CATALOG_PATHS.has(path)) {
    if (path === MODELS_ROOT) {
      return "MODEL";
    }
    const byPath: Record<string, ObjectType> = {
      "root.platform": "PLATFORM",
      "root.platform.devices": "DEVICES",
      "root.platform.dashboards": "DASHBOARDS",
      "root.platform.reports": "REPORTS",
      "root.platform.workflows": "WORKFLOWS",
      "root.platform.alert-rules": "ALERT_RULES",
      "root.platform.correlators": "CORRELATORS",
      "root.platform.applications": "APPLICATIONS",
      "root.platform.operator-apps": "OPERATOR_APPS",
      "root.platform.security": "SECURITY",
    };
    return byPath[path] ?? null;
  }
  if (isApplicationSubfolder(path)) {
    if (path.endsWith(".functions")) return "FUNCTIONS";
    if (path.endsWith(".reports")) return "REPORTS";
    if (path.endsWith(".schedules")) return "SCHEDULES";
    if (path.endsWith(".bindings")) return "BINDINGS";
    if (path.endsWith(".migrations")) return "MIGRATIONS";
    if (path.endsWith(".screens")) return "SCREENS";
  }
  return null;
}

export function isSystemCatalogFolder(path: string, objectType?: ObjectType): boolean {
  if (
    isSecurityUsersRoot(path)
    || isSecurityRolesRoot(path)
    || isSecurityUserPath(path)
    || isSecurityRolePath(path)
    || isOperatorAppChildPath(path)
    || isFederationRoot(path)
    || isTenantsRoot(path)
    || (isAlertRulePath(path) && !isAlertRulesRoot(path))
    || (isCorrelatorPath(path) && !isCorrelatorsRoot(path))
  ) {
    return false;
  }

  if (objectType === "MODEL" && path !== MODELS_ROOT) {
    return false;
  }

  return resolveCatalogType(path, objectType) !== null;
}

export function getSystemFolderListMeta(
  path: string,
  objectType?: ObjectType,
  displayName?: string,
  description?: string,
): SystemFolderListMeta {
  const catalogType = resolveCatalogType(path, objectType);
  if (catalogType === "MODEL") {
    return {
      ...MODELS_FOLDER_META,
      title: displayName?.trim() || MODELS_FOLDER_META.title,
    };
  }
  if (catalogType && META_BY_TYPE[catalogType]) {
    const meta = META_BY_TYPE[catalogType]!;
    return {
      ...meta,
      title: displayName?.trim() || meta.title,
      description: description?.trim() || meta.description,
    };
  }
  return {
    title: displayName?.trim() || path.split(".").pop() || path,
    description:
      description?.trim()
      || `Объекты в ${path}. Выберите элемент в дереве или в списке.`,
    idColumnLabel: "ID",
  };
}

export function childIdFromPath(parentPath: string, childPath: string): string {
  if (!childPath.startsWith(`${parentPath}.`)) {
    return childPath.split(".").pop() ?? childPath;
  }
  return childPath.slice(parentPath.length + 1);
}
