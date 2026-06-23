import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { createSecurityUser } from "../api/securityUsers";
import { fetchSecurityRoles } from "../api/securityRoles";

interface CreateSecurityUserDialogProps {
  onClose: () => void;
  onCreated: (objectPath: string) => void;
}

export default function CreateSecurityUserDialog({
  onClose,
  onCreated,
}: CreateSecurityUserDialogProps) {
  const { t } = useTranslation(["security", "common"]);
  const [username, setUsername] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState("operator");

  const rolesQuery = useQuery({
    queryKey: ["security-roles"],
    queryFn: fetchSecurityRoles,
  });

  const availableRoles = rolesQuery.data ?? [];

  const mutation = useMutation({
    mutationFn: () =>
      createSecurityUser({
        username: username.trim(),
        displayName: displayName.trim() || username.trim(),
        password,
        roles: [role],
      }),
    onSuccess: (created) => {
      onCreated(created.objectPath);
      onClose();
    },
  });

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal modal-create-object" onClick={(e) => e.stopPropagation()}>
        <header>
          <h3>{t("createUser.title")}</h3>
          <button type="button" className="icon-btn" onClick={onClose}>✕</button>
        </header>
        <form
          className="modal-body form-grid"
          onSubmit={(event) => {
            event.preventDefault();
            mutation.mutate();
          }}
        >
          <label className="full">
            {t("users.column.login")}
            <input
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="operator2"
              required
              pattern="[a-zA-Z0-9._-]{2,64}"
              autoFocus
            />
          </label>
          <label className="full">
            {t("common:field.displayName")}
            <input
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              placeholder="Operator 2"
            />
          </label>
          <label className="full">
            {t("createUser.password")}
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={4}
            />
          </label>
          <label className="full">
            {t("user.role")}
            <select
              value={role}
              onChange={(e) => setRole(e.target.value)}
              disabled={availableRoles.length === 0}
            >
              {availableRoles.map((item) => (
                <option key={item.name} value={item.name}>
                  {item.name}{item.description ? ` — ${item.description}` : ""}
                </option>
              ))}
            </select>
          </label>
          {mutation.error && (
            <p className="hint error full">{String(mutation.error)}</p>
          )}
          <footer className="full form-actions">
            <button type="button" className="btn" onClick={onClose}>{t("common:action.cancel")}</button>
            <button type="submit" className="btn primary" disabled={mutation.isPending}>
              {t("common:action.create")}
            </button>
          </footer>
        </form>
      </div>
    </div>
  );
}
