import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { fetchObjectAcl, saveObjectAcl, type ObjectAclEntry } from "../api/objectAcl";

interface ObjectAclPanelProps {
  objectPath: string;
  canManage: boolean;
}

const EMPTY_ENTRY: ObjectAclEntry = {
  principalType: "ROLE",
  principalId: "operator",
  permission: "READ",
};

export default function ObjectAclPanel({ objectPath, canManage }: ObjectAclPanelProps) {
  const { t } = useTranslation(["security", "common"]);
  const queryClient = useQueryClient();
  const [entries, setEntries] = useState<ObjectAclEntry[]>([]);
  const dirtyRef = useRef(false);
  const loadedPathRef = useRef<string | null>(null);

  const aclQuery = useQuery({
    queryKey: ["object-acl", objectPath],
    queryFn: () => fetchObjectAcl(objectPath),
  });

  useEffect(() => {
    if (aclQuery.data && (!dirtyRef.current || loadedPathRef.current !== objectPath)) {
      setEntries(aclQuery.data);
      dirtyRef.current = false;
      loadedPathRef.current = objectPath;
    }
  }, [aclQuery.data, objectPath]);

  const saveMutation = useMutation({
    mutationFn: () => saveObjectAcl(objectPath, entries),
    onSuccess: () => {
      dirtyRef.current = false;
      queryClient.invalidateQueries({ queryKey: ["object-acl", objectPath] });
    },
  });

  const updateEntry = (index: number, patch: Partial<ObjectAclEntry>) => {
    dirtyRef.current = true;
    setEntries((current) =>
      current.map((entry, entryIndex) => (entryIndex === index ? { ...entry, ...patch } : entry))
    );
  };

  const removeEntry = (index: number) => {
    dirtyRef.current = true;
    setEntries((current) => current.filter((_, entryIndex) => entryIndex !== index));
  };

  return (
    <section className="security-users-panel">
      <header className="security-users-header">
        <div>
          <h3>{t("acl.title")}</h3>
          <p className="op-muted">{t("acl.subtitle", { path: objectPath })}</p>
        </div>
        {canManage && (
          <button type="button" className="btn" onClick={() => { dirtyRef.current = true; setEntries((current) => [...current, { ...EMPTY_ENTRY }]); }}>
            {t("acl.addRule")}
          </button>
        )}
      </header>

      {aclQuery.isLoading && <p className="op-muted">{t("common:action.loading")}</p>}
      {aclQuery.error && <div className="op-alert op-alert-error">{String(aclQuery.error)}</div>}

      {entries.length === 0 && !aclQuery.isLoading && (
        <p className="op-muted">{t("acl.empty")}</p>
      )}

      {entries.length > 0 && (
        <table className="op-table security-users-table security-users-table-compact">
          <thead>
            <tr>
              <th>{t("acl.column.type")}</th>
              <th>{t("acl.column.principal")}</th>
              <th>{t("acl.column.permission")}</th>
              {canManage && <th />}
            </tr>
          </thead>
          <tbody>
            {entries.map((entry, index) => (
              <tr key={`${entry.principalType}-${entry.principalId}-${entry.permission}-${index}`}>
                <td>
                  <select
                    value={entry.principalType}
                    disabled={!canManage}
                    onChange={(event) =>
                      updateEntry(index, { principalType: event.target.value as ObjectAclEntry["principalType"] })
                    }
                  >
                    <option value="ROLE">ROLE</option>
                    <option value="USER">USER</option>
                  </select>
                </td>
                <td>
                  <input
                    value={entry.principalId}
                    disabled={!canManage}
                    onChange={(event) => updateEntry(index, { principalId: event.target.value })}
                  />
                </td>
                <td>
                  <select
                    value={entry.permission}
                    disabled={!canManage}
                    onChange={(event) =>
                      updateEntry(index, { permission: event.target.value as ObjectAclEntry["permission"] })
                    }
                  >
                    <option value="READ">READ</option>
                    <option value="WRITE">WRITE</option>
                    <option value="INVOKE">INVOKE</option>
                  </select>
                </td>
                {canManage && (
                  <td>
                    <button type="button" className="btn danger" onClick={() => removeEntry(index)}>
                      {t("common:action.delete")}
                    </button>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {canManage && (
        <div className="operator-apps-actions">
          <button
            type="button"
            className="btn primary"
            disabled={saveMutation.isPending}
            onClick={() => saveMutation.mutate()}
          >
            {saveMutation.isPending ? t("common:action.saving") : t("acl.save")}
          </button>
          {saveMutation.error && <div className="op-alert op-alert-error">{String(saveMutation.error)}</div>}
        </div>
      )}
    </section>
  );
}
