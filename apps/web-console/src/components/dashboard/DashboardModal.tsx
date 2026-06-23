import DashboardBuilder from "./DashboardBuilder";
import type { DashboardSession } from "./DashboardContext";
import type { OpenDashboardOptions } from "./DashboardContext";

interface DashboardModalProps {
  path: string;
  title: string;
  session?: DashboardSession;
  selection?: Record<string, string>;
  params?: Record<string, unknown>;
  onSessionChange?: (next: DashboardSession) => void;
  onSelectionChange?: (next: Record<string, string>) => void;
  onParamsChange?: (next: Record<string, unknown>) => void;
  onNavigateDashboard?: (path: string, options?: OpenDashboardOptions) => void;
  onOpenDashboardModal?: (
    path: string,
    title?: string,
    options?: OpenDashboardOptions
  ) => void;
  onClose: () => void;
}

export default function DashboardModal({
  path,
  title,
  session,
  selection,
  params,
  onSessionChange,
  onSelectionChange,
  onParamsChange,
  onNavigateDashboard,
  onOpenDashboardModal,
  onClose,
}: DashboardModalProps) {
  return (
    <div className="modal-backdrop dashboard-modal-backdrop" onClick={onClose}>
      <div className="modal dashboard-modal" onClick={(event) => event.stopPropagation()}>
        <header>
          <h3>{title}</h3>
          <button type="button" className="btn" onClick={onClose}>
            Закрыть
          </button>
        </header>
        <div className="dashboard-modal-body">
          <DashboardBuilder
            path={path}
            operatorMode
            embeddedModal
            session={session}
            selection={selection}
            params={params}
            onSessionChange={onSessionChange}
            onSelectionChange={onSelectionChange}
            onParamsChange={onParamsChange}
            onNavigateDashboard={onNavigateDashboard}
            onOpenDashboardModal={onOpenDashboardModal}
          />
        </div>
      </div>
    </div>
  );
}
