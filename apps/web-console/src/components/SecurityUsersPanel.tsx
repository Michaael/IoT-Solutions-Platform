import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo } from "react";
import {
  createSecurityUser,
  fetchSecurityUsers,
  setSecurityUserPassword,
  updateSecurityUser,
  type SecurityUserSummary,
} from "../api/securityUsers";
import { fetchOperatorApps, type OperatorAppEntry } from "../api/operatorApps";

async function loadOperatorApps(): Promise<OperatorAppEntry[]> {
  try {
    const apps = await fetchOperatorApps();
    return apps.length ? apps : [{ appId: "platform", title: "Platform HMI" }];
  } catch {
    return [{ appId: "platform", title: "Platform HMI" }];
  }
}

function serverSupportsAutoStart(users: SecurityUserSummary[] | undefined): boolean {
  return Boolean(users?.some((user) => Object.prototype.hasOwnProperty.call(user, "autoStartEnabled")));
}

interface UserAutoStartControlsProps {
  user: SecurityUserSummary;
  apps: OperatorAppEntry[];
  serverReady: boolean;
}

function UserAutoStartControls({ user, apps, serverReady }: UserAutoStartControlsProps) {
  const queryClient = useQueryClient();
  const defaultApp = apps[0]?.appId ?? "platform";
  const enabled = user.autoStartEnabled === true;
  const selectedApp = user.autoStartApp || defaultApp;

  const mutation = useMutation({
    mutationFn: async (payload: Parameters<typeof updateSecurityUser>[1]) => {
      const updated = await updateSecurityUser(user.username, payload);
      if (payload.autoStartEnabled === true && updated.autoStartEnabled !== true) {
        throw new Error(
          "Сервер не сохранил автозапуск. Перезапустите ispf-server (миграция V16)."
        );
      }
      if (
        payload.autoStartApp &&
        payload.autoStartEnabled !== false &&
        updated.autoStartApp !== payload.autoStartApp
      ) {
        throw new Error(
          "Сервер не сохранил приложение. Перезапустите ispf-server (миграция V16)."
        );
      }
      return updated;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["security-users"] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
    },
  });

  const disabled = !serverReady || mutation.isPending;

  return (
    <div className="security-user-autostart">
      <button
        type="button"
        className={`btn small security-user-autostart-toggle ${enabled ? "primary" : ""}`}
        disabled={disabled}
        onClick={() => {
          const nextEnabled = !enabled;
          mutation.mutate({
            autoStartEnabled: nextEnabled,
            autoStartApp: nextEnabled ? selectedApp : user.autoStartApp ?? null,
          });
        }}
      >
        {mutation.isPending ? "…" : enabled ? "Вкл" : "Выкл"}
      </button>
      <select
        className="security-user-autostart-app"
        value={user.autoStartApp ?? ""}
        disabled={disabled}
        onChange={(event) => {
          const appId = event.target.value;
          if (!appId) {
            mutation.mutate({ autoStartEnabled: false, autoStartApp: null });
            return;
          }
          mutation.mutate({ autoStartEnabled: true, autoStartApp: appId });
        }}
      >
        <option value="">— без приложения —</option>
        {apps.map((app) => (
          <option key={app.appId} value={app.appId}>
            {app.title} ({app.appId})
          </option>
        ))}
      </select>
      {mutation.error && (
        <span className="hint error security-user-autostart-error">{String(mutation.error)}</span>
      )}
    </div>
  );
}

interface SecurityUsersPanelProps {
  canManage: boolean;
}

export default function SecurityUsersPanel({ canManage }: SecurityUsersPanelProps) {
  const queryClient = useQueryClient();
  const usersQuery = useQuery({
    queryKey: ["security-users"],
    queryFn: fetchSecurityUsers,
  });
  const appsQuery = useQuery({
    queryKey: ["operator-apps"],
    queryFn: loadOperatorApps,
  });

  const apps = useMemo(() => appsQuery.data ?? [], [appsQuery.data]);
  const serverReady = serverSupportsAutoStart(usersQuery.data);

  const createMutation = useMutation({
    mutationFn: createSecurityUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["security-users"] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
    },
  });

  const passwordMutation = useMutation({
    mutationFn: ({ username, password }: { username: string; password: string }) =>
      setSecurityUserPassword(username, password),
  });

  if (!canManage) {
    return <p className="op-muted">Управление пользователями доступно только admin.</p>;
  }

  return (
    <section className="security-users-panel">
      <h3>Пользователи платформы</h3>
      <p className="op-muted">
        Учётные записи: <code>root.platform.security.users</code>. Автозапуск открывает
        operator-приложение с дашбордами (<code>?mode=operator&amp;app=…</code>). В списке —
        <code>platform</code> (настройка в <code>Operator Apps</code>) и deploy-приложения с{" "}
        <code>operatorUi</code>/<code>dashboards[]</code> в bundle. Узел <code>Applications</code> —
        backend (функции, отчёты), не меню operator.
      </p>
      {!serverReady && usersQuery.data && (
        <div className="op-alert op-alert-error">
          Автозапуск недоступен: сервер без миграции V16. Перезапустите{" "}
          <code>ispf-server</code> с актуальным кодом.
        </div>
      )}
      {usersQuery.error && <div className="op-alert op-alert-error">{String(usersQuery.error)}</div>}
      <table className="op-table security-users-table">
        <thead>
          <tr>
            <th>Логин</th>
            <th>Имя</th>
            <th>Роли</th>
            <th>Активен</th>
            <th colSpan={2}>Автозапуск приложения</th>
            <th>Пароль</th>
          </tr>
        </thead>
        <tbody>
          {(usersQuery.data ?? []).map((user) => (
            <tr key={user.username}>
              <td>{user.username}</td>
              <td>{user.displayName}</td>
              <td>{user.roles.join(", ")}</td>
              <td>{user.enabled ? "да" : "нет"}</td>
              <td colSpan={2}>
                <UserAutoStartControls user={user} apps={apps} serverReady={serverReady} />
              </td>
              <td>
                <button
                  type="button"
                  className="btn small"
                  onClick={() => {
                    const password = window.prompt(`Новый пароль для ${user.username}`);
                    if (password) {
                      passwordMutation.mutate({ username: user.username, password });
                    }
                  }}
                >
                  Сменить
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <form
        className="security-user-form"
        onSubmit={(event) => {
          event.preventDefault();
          const form = event.currentTarget;
          const data = new FormData(form);
          createMutation.mutate({
            username: String(data.get("username") ?? ""),
            displayName: String(data.get("displayName") ?? ""),
            password: String(data.get("password") ?? ""),
            roles: [String(data.get("role") ?? "operator")],
          });
          form.reset();
        }}
      >
        <h4>Добавить пользователя</h4>
        <div className="security-user-form-grid">
          <input name="username" placeholder="Логин" required />
          <input name="displayName" placeholder="Отображаемое имя" />
          <input name="password" type="password" placeholder="Пароль" required />
          <select name="role" defaultValue="operator">
            <option value="operator">operator</option>
            <option value="admin">admin</option>
          </select>
          <button type="submit" className="btn primary" disabled={createMutation.isPending}>
            Создать
          </button>
        </div>
      </form>
    </section>
  );
}
