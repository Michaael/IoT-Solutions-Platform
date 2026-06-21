import { useQuery } from "@tanstack/react-query";
import { fetchObjects } from "../api";
import type { ObjectSummary } from "../types";
import {
  childIdFromPath,
  getSystemFolderListMeta,
  type SystemFolderListMeta,
} from "../utils/systemFolderConfig";
import type { ObjectType } from "../types";
import { isSpecializedEditorObject } from "../utils/editorObject";

interface SystemFolderListPanelProps {
  folderPath: string;
  folderType?: ObjectType;
  folderDisplayName?: string;
  folderDescription?: string;
  canManage: boolean;
  createLabel?: string;
  onCreateChild?: () => void;
  onSelectPath: (path: string) => void;
  onOpenEditor?: (path: string) => void;
}

function sortChildren(items: ObjectSummary[]): ObjectSummary[] {
  return [...items].sort((a, b) => {
    if (a.sortOrder !== b.sortOrder) {
      return a.sortOrder - b.sortOrder;
    }
    return a.path.localeCompare(b.path);
  });
}

export default function SystemFolderListPanel({
  folderPath,
  folderType,
  folderDisplayName,
  folderDescription,
  canManage,
  createLabel,
  onCreateChild,
  onSelectPath,
  onOpenEditor,
}: SystemFolderListPanelProps) {
  const meta: SystemFolderListMeta = getSystemFolderListMeta(
    folderPath,
    folderType,
    folderDisplayName,
    folderDescription,
  );

  const childrenQuery = useQuery({
    queryKey: ["objects", folderPath],
    queryFn: () => fetchObjects(folderPath),
  });

  const children = sortChildren(childrenQuery.data ?? []);
  const showCreate = canManage && createLabel && onCreateChild;

  return (
    <section className="security-users-panel">
      <header className="security-users-header">
        <div>
          <h3>{meta.title}</h3>
          <p className="op-muted">{meta.description}</p>
        </div>
        {showCreate && (
          <button type="button" className="btn primary" onClick={onCreateChild}>
            {createLabel}
          </button>
        )}
      </header>

      {childrenQuery.isLoading && <p className="op-muted">Загрузка…</p>}
      {childrenQuery.error && (
        <div className="op-alert op-alert-error">{String(childrenQuery.error)}</div>
      )}

      {!childrenQuery.isLoading && !childrenQuery.error && children.length === 0 && (
        <p className="op-muted">
          {showCreate ? "Нет объектов. Создайте первый объект кнопкой выше." : "Список пуст."}
        </p>
      )}

      {children.length > 0 && (
        <table className="op-table security-users-table security-users-table-compact">
          <thead>
            <tr>
              <th>{meta.idColumnLabel}</th>
              <th>Название</th>
              <th>Тип</th>
              <th>Шаблон</th>
              <th>Описание</th>
              {onOpenEditor && <th>Действия</th>}
            </tr>
          </thead>
          <tbody>
            {children.map((child) => {
              const canOpenEditor = Boolean(
                onOpenEditor && isSpecializedEditorObject(child.path, child.type, child.templateId),
              );
              return (
              <tr
                key={child.path}
                className={canOpenEditor ? "catalog-row-openable" : undefined}
                onDoubleClick={() => {
                  if (canOpenEditor) {
                    onOpenEditor?.(child.path);
                  }
                }}
              >
                <td>
                  <button
                    type="button"
                    className="link-btn"
                    onClick={() => onSelectPath(child.path)}
                  >
                    <code>{childIdFromPath(folderPath, child.path)}</code>
                  </button>
                </td>
                <td>{child.displayName}</td>
                <td>
                  <code>{child.type}</code>
                </td>
                <td>{child.templateId ? <code>{child.templateId}</code> : "—"}</td>
                <td>{child.description || "—"}</td>
                {onOpenEditor && (
                  <td>
                    {canOpenEditor ? (
                      <button
                        type="button"
                        className="btn btn-sm"
                        onClick={() => onOpenEditor(child.path)}
                      >
                        Открыть
                      </button>
                    ) : (
                      "—"
                    )}
                  </td>
                )}
              </tr>
            );
            })}
          </tbody>
        </table>
      )}
    </section>
  );
}
