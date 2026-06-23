import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { updateSecurityUser, type SecurityUserSummary } from "../api/securityUsers";
import type { OperatorAppEntry } from "../api/operatorApps";

interface SecurityUserAutoStartFieldsProps {
  user: SecurityUserSummary;
  apps: OperatorAppEntry[];
  serverReady: boolean;
  disabled?: boolean;
}

export default function SecurityUserAutoStartFields({
  user,
  apps,
  serverReady,
  disabled = false,
}: SecurityUserAutoStartFieldsProps) {
  const { t } = useTranslation(["security", "common"]);
  const queryClient = useQueryClient();
  const defaultApp = apps[0]?.appId ?? "platform";
  const enabled = user.autoStartEnabled === true;
  const selectedApp = user.autoStartApp || defaultApp;

  const mutation = useMutation({
    mutationFn: async (payload: Parameters<typeof updateSecurityUser>[1]) => {
      const updated = await updateSecurityUser(user.username, payload);
      if (payload.autoStartEnabled === true && updated.autoStartEnabled !== true) {
        throw new Error(t("autostart.saveFailed"));
      }
      return updated;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["security-users"] });
      queryClient.invalidateQueries({ queryKey: ["security-user", user.username] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      queryClient.invalidateQueries({ queryKey: ["variables"] });
    },
  });

  const controlsDisabled = disabled || !serverReady || mutation.isPending;

  return (
    <div className="security-user-autostart">
      <div className="security-user-switch-field">
        <div>
          <span className="field-label">{t("autostart.onLogin")}</span>
          <p className="security-user-switch-hint">
            {enabled ? t("autostart.enabledHint") : t("autostart.disabledHint")}
          </p>
        </div>
        <label className="switch">
          <input
            type="checkbox"
            checked={enabled}
            disabled={controlsDisabled}
            onChange={() => {
              const nextEnabled = !enabled;
              mutation.mutate({
                autoStartEnabled: nextEnabled,
                autoStartApp: nextEnabled ? selectedApp : user.autoStartApp ?? null,
              });
            }}
          />
          <span className="switch-slider" aria-hidden />
        </label>
      </div>

      <label className="security-user-autostart-select">
        <span className="field-label">{t("autostart.app")}</span>
        <select
          value={user.autoStartApp ?? ""}
          disabled={controlsDisabled || !enabled}
          onChange={(event) => {
            const appId = event.target.value;
            if (!appId) {
              mutation.mutate({ autoStartEnabled: false, autoStartApp: null });
              return;
            }
            mutation.mutate({ autoStartEnabled: true, autoStartApp: appId });
          }}
        >
          <option value="">{t("autostart.notSelected")}</option>
          {apps.map((app) => (
            <option key={app.appId} value={app.appId}>
              {app.title} ({app.appId})
            </option>
          ))}
        </select>
      </label>

      {!serverReady && (
        <p className="hint">{t("autostart.serverNotReady")}</p>
      )}
      {mutation.error && (
        <p className="hint error">{String(mutation.error)}</p>
      )}
    </div>
  );
}
