import DashboardBuilder from "./DashboardBuilder";

interface DashboardModalProps {
  path: string;
  title: string;
  selection?: Record<string, string>;
  onSelectionChange?: (next: Record<string, string>) => void;
  onNavigateDashboard?: (path: string) => void;
  onOpenDashboardModal?: (path: string, title?: string) => void;
  onClose: () => void;
}

export default function DashboardModal({
  path,
  title,
  selection,
  onSelectionChange,
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
            selection={selection}
            onSelectionChange={onSelectionChange}
            onNavigateDashboard={onNavigateDashboard}
            onOpenDashboardModal={onOpenDashboardModal}
          />
        </div>
      </div>
    </div>
  );
}
