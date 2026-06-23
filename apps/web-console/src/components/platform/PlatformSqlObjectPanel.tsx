import { useTranslation } from "react-i18next";
import {
  isDataSourcePath,
  isMigrationPath,
  isSqlBindingPath,
} from "../../utils/platformSqlPath";

interface PlatformSqlObjectPanelProps {
  path: string;
  onOpenEditor: (path: string) => void;
}

export default function PlatformSqlObjectPanel({ path, onOpenEditor }: PlatformSqlObjectPanelProps) {
  const { t } = useTranslation(["platform", "common"]);

  function titleForPath(objectPath: string): string {
    if (isDataSourcePath(objectPath)) {
      return t("platform:dataSource.title");
    }
    if (isMigrationPath(objectPath)) {
      return t("platform:migration.title");
    }
    if (isSqlBindingPath(objectPath)) {
      return t("platform:sqlBinding.title");
    }
    return t("platform:sqlObject.title");
  }

  function descriptionForPath(objectPath: string): string {
    if (isDataSourcePath(objectPath)) {
      return t("platform:sqlObject.dataSourceDesc");
    }
    if (isMigrationPath(objectPath)) {
      return t("platform:sqlObject.migrationDesc");
    }
    if (isSqlBindingPath(objectPath)) {
      return t("platform:sqlObject.bindingDesc");
    }
    return "";
  }

  return (
    <section className="security-users-panel">
      <header className="security-users-header">
        <div>
          <h3>{titleForPath(path)}</h3>
          <p className="op-muted">{descriptionForPath(path)}</p>
          <p className="hint mono small">{path}</p>
        </div>
        <button type="button" className="btn primary" onClick={() => onOpenEditor(path)}>
          {t("common:action.openInEditor")}
        </button>
      </header>
      <p className="op-muted">
        {t("platform:hint.openInEditorWorkspace")}
      </p>
    </section>
  );
}
