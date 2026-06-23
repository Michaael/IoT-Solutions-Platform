import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { fetchSecurityUsers } from "../api/securityUsers";
import CreateSecurityUserDialog from "./CreateSecurityUserDialog";

interface SecurityUsersPanelProps {
  canManage: boolean;
  onSelectUser: (path: string) => void;
}

export default function SecurityUsersPanel({ canManage, onSelectUser }: SecurityUsersPanelProps) {
  const { t } = useTranslation(["security", "common"]);
  const [showCreate, setShowCreate] = useState(false);

  const usersQuery = useQuery({
    queryKey: ["security-users"],
    queryFn: fetchSecurityUsers,
  });

  if (!canManage) {
    return <p className="op-muted">{t("users.adminOnly")}</p>;
  }

  return (
    <section className="security-users-panel">
      <header className="security-users-header">
        <div>
          <h3>{t("users.title")}</h3>
          <p className="op-muted">{t("users.subtitle")}</p>
        </div>
        <button type="button" className="btn primary" onClick={() => setShowCreate(true)}>
          {t("users.create")}
        </button>
      </header>

      {usersQuery.error && <div className="op-alert op-alert-error">{String(usersQuery.error)}</div>}

      <table className="op-table security-users-table security-users-table-compact">
        <thead>
          <tr>
            <th>{t("users.column.login")}</th>
            <th>{t("users.column.displayName")}</th>
            <th>{t("users.column.roles")}</th>
            <th>{t("users.column.active")}</th>
          </tr>
        </thead>
        <tbody>
          {(usersQuery.data ?? []).map((user) => (
            <tr key={user.username}>
              <td>
                <button
                  type="button"
                  className="link-btn"
                  onClick={() => onSelectUser(user.objectPath)}
                >
                  <code>{user.username}</code>
                </button>
              </td>
              <td>{user.displayName}</td>
              <td>{user.roles.join(", ")}</td>
              <td>{user.enabled ? t("common:action.yes") : t("common:action.no")}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {showCreate && (
        <CreateSecurityUserDialog
          onClose={() => setShowCreate(false)}
          onCreated={(objectPath) => {
            setShowCreate(false);
            onSelectUser(objectPath);
          }}
        />
      )}
    </section>
  );
}
