import ObjectInspector from "./ObjectInspector";
import OperatorAppsPanel from "./OperatorAppsPanel";
import SecurityUsersPanel from "./SecurityUsersPanel";
import { isModelsPath } from "../types/models";
import type { ObjectSummary } from "../types";
import {
  isOperatorAppChildPath,
  isOperatorAppsRootPath,
} from "../utils/operatorAppsPath";
import { APPLICATIONS_ROOT, canCreateChildAt, createActionLabel } from "../utils/createObjectMode";

interface ExplorerViewProps {
  selectedPath: string | null;
  selectedObject: ObjectSummary | null;
  onOpenEditor: (path: string) => void;
  onCreateChild: () => void;
  onDeleted: () => void;
  isAdmin: boolean;
}

export default function ExplorerView({
  selectedPath,
  selectedObject,
  onOpenEditor,
  onCreateChild,
  onDeleted,
  isAdmin,
}: ExplorerViewProps) {
  if (!selectedPath) {
    return <div className="inspector-empty">Выберите объект в дереве</div>;
  }

  const isOperatorAppChild = isOperatorAppChildPath(selectedPath);
  const isOperatorAppsRoot = isOperatorAppsRootPath(selectedPath);
  const isApplicationsRoot = selectedPath === APPLICATIONS_ROOT;
  const showCreateButton =
    isAdmin && canCreateChildAt(selectedPath, selectedObject?.type);

  return (
    <div
      className={`explorer-view${isOperatorAppChild ? " explorer-view-operator-app" : ""}`}
    >
      {!isOperatorAppChild && (
        <div className="explorer-toolbar">
          {showCreateButton && (
            <button type="button" className="btn primary" onClick={onCreateChild}>
              {createActionLabel(selectedPath)}
            </button>
          )}
          <button type="button" className="btn" onClick={() => onOpenEditor(selectedPath)}>
            Открыть в редакторе
          </button>
          <span className="hint">
            {isModelsPath(selectedPath)
              ? "Полное определение модели — в редакторе (кнопка выше или двойной щелчок)"
              : "Двойной щелчок по узлу также открывает редактор"}
          </span>
        </div>
      )}

      {isOperatorAppChild ? (
        <OperatorAppsPanel canManage={isAdmin} selectedPath={selectedPath} />
      ) : (
        <>
          <ObjectInspector path={selectedPath} onDeleted={onDeleted} canManage={isAdmin} />
          {isApplicationsRoot && (
            <section className="operator-apps-folder-hint">
              <p className="hint">
                Deploy-приложения (функции, отчёты, bundle). Укажите App ID и название, затем
                deploy bundle через API.
              </p>
            </section>
          )}
          {isOperatorAppsRoot && (
            <section className="operator-apps-folder-hint">
              <p className="hint">
                Operator UI настраивается в дочерних объектах. Новое приложение — кнопка выше.
              </p>
            </section>
          )}
        </>
      )}

      {selectedPath === "root.platform.security.users" && (
        <SecurityUsersPanel canManage={isAdmin} />
      )}
    </div>
  );
}
