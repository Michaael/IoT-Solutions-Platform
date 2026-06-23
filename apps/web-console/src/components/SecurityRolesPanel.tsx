import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { fetchSecurityRoles } from "../api/securityRoles";
import CreateSecurityRoleDialog from "./CreateSecurityRoleDialog";

interface SecurityRolesPanelProps {
  canManage: boolean;
  onSelectRole: (path: string) => void;
}

export default function SecurityRolesPanel({ canManage, onSelectRole }: SecurityRolesPanelProps) {
  const { t } = useTranslation(["security", "common"]);
  const [showCreate, setShowCreate] = useState(false);
  const queryClient = useQueryClient();

  const rolesQuery = useQuery({
    queryKey: ["security-roles"],
    queryFn: fetchSecurityRoles,
  });

  if (!canManage) {
    return <p className="op-muted">{t("roles.adminOnly")}</p>;
  }

  return (
    <section className="security-users-panel">
      <header className="security-users-header">
        <div>
          <h3>{t("roles.title")}</h3>
          <p className="op-muted">{t("roles.subtitle")}</p>
        </div>
        <button type="button" className="btn primary" onClick={() => setShowCreate(true)}>
          {t("roles.create")}
        </button>
      </header>

      {rolesQuery.error && <div className="op-alert op-alert-error">{String(rolesQuery.error)}</div>}

      <table className="op-table security-users-table security-users-table-compact">
        <thead>
          <tr>
            <th>{t("roles.column.name")}</th>
            <th>{t("roles.column.displayName")}</th>
            <th>{t("roles.column.description")}</th>
            <th>{t("roles.column.type")}</th>
          </tr>
        </thead>
        <tbody>
          {(rolesQuery.data ?? []).map((role) => (
            <tr key={role.name}>
              <td>
                <button
                  type="button"
                  className="link-btn"
                  onClick={() => onSelectRole(role.objectPath)}
                >
                  <code>{role.name}</code>
                </button>
              </td>
              <td>{role.displayName}</td>
              <td>{role.description || t("common:empty.dash")}</td>
              <td>{role.builtIn ? t("roles.type.builtIn") : t("roles.type.custom")}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {showCreate && (
        <CreateSecurityRoleDialog
          onClose={() => setShowCreate(false)}
          onCreated={(objectPath) => {
            queryClient.invalidateQueries({ queryKey: ["security-roles"] });
            queryClient.invalidateQueries({ queryKey: ["objects"] });
            setShowCreate(false);
            onSelectRole(objectPath);
          }}
        />
      )}
    </section>
  );
}
