import type { AuthSession } from "../../auth/session";
import OperatorAppLauncher from "./OperatorAppLauncher";
import OperatorDashboardApp from "./OperatorDashboardApp";

interface OperatorViewProps {
  operatorId?: string;
  appId?: string | null;
  onSwitchAdmin?: () => void;
  onSelectApp?: (appId: string) => void;
  session?: AuthSession;
  onLogout?: () => void;
}

export default function OperatorView({
  operatorId = "operator",
  appId = null,
  onSwitchAdmin,
  onSelectApp,
  session,
  onLogout,
}: OperatorViewProps) {
  if (!appId) {
    return (
      <OperatorAppLauncher
        onOpenApp={(nextAppId) => onSelectApp?.(nextAppId)}
        onSwitchAdmin={onSwitchAdmin}
      />
    );
  }

  return (
    <OperatorDashboardApp
      appId={appId}
      operatorId={operatorId}
      onSwitchAdmin={onSwitchAdmin}
      session={session}
      onLogout={onLogout}
    />
  );
}
