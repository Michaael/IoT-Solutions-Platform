import { useTranslation } from "react-i18next";
import type { ActiveOperatorAlarm } from "../../types/operatorAlarmBar";

export interface AlarmBarOverlayProps {
  enabled: boolean;
  position: "top" | "bottom";
  alarms: ActiveOperatorAlarm[];
  muted: boolean;
  toggleMute: () => void;
  onDismiss: (alarmId: string) => void;
  onOpenDashboard: (alarm: ActiveOperatorAlarm) => void;
  onOpenReport: (alarm: ActiveOperatorAlarm) => void;
  onOpenObject: (alarm: ActiveOperatorAlarm) => void;
}

function AlarmBarItem({
  alarm,
  showMute,
  muted,
  toggleMute,
  onDismiss,
  onOpenDashboard,
  onOpenReport,
  onOpenObject,
}: {
  alarm: ActiveOperatorAlarm;
  showMute: boolean;
  muted: boolean;
  toggleMute: () => void;
  onDismiss: () => void;
  onOpenDashboard: () => void;
  onOpenReport: () => void;
  onOpenObject: () => void;
}) {
  const { t } = useTranslation("operator");
  const isCritical = alarm.event.level === "CRITICAL";
  const { colors } = alarm;

  return (
    <div
      className={`operator-alarm-bar${isCritical ? " operator-alarm-bar--critical" : ""}`}
      style={{
        background: colors.background,
        color: colors.text,
        borderColor: colors.border,
        ["--alarm-accent" as string]: colors.accent,
      }}
      role="alert"
      aria-live="assertive"
    >
      <div className="operator-alarm-bar-body">
        <div className="operator-alarm-bar-main">
          <span className="operator-alarm-bar-level">{alarm.event.level}</span>
          <strong className="operator-alarm-bar-title">{alarm.title}</strong>
        </div>
        <dl className="operator-alarm-bar-fields">
          {alarm.fieldRows.map((field) => (
            <div key={`${field.label}-${field.value}`} className="operator-alarm-bar-field">
              <dt>{field.label}</dt>
              <dd>{field.value}</dd>
            </div>
          ))}
        </dl>
      </div>
      <div className="operator-alarm-bar-actions">
        {alarm.dashboardPath && (
          <button type="button" className="btn operator-alarm-bar-btn" onClick={onOpenDashboard}>
            {t("alarmBar.dashboard")}
          </button>
        )}
        {alarm.reportPath && (
          <button type="button" className="btn operator-alarm-bar-btn" onClick={onOpenReport}>
            {t("alarmBar.report")}
          </button>
        )}
        <button type="button" className="btn operator-alarm-bar-btn" onClick={onOpenObject}>
          {t("alarmBar.toObject")}
        </button>
        {showMute && (
          <button
            type="button"
            className={`btn operator-alarm-bar-btn${muted ? " operator-alarm-bar-btn-muted" : ""}`}
            onClick={toggleMute}
            title={muted ? t("alarmBar.unmute") : t("alarmBar.mute")}
          >
            {muted ? "🔇" : "🔊"}
          </button>
        )}
        <button type="button" className="btn primary operator-alarm-bar-btn" onClick={onDismiss}>
          {t("alarmBar.acknowledge")}
        </button>
      </div>
    </div>
  );
}

export default function AlarmBarOverlay({
  enabled,
  position,
  alarms,
  muted,
  toggleMute,
  onDismiss,
  onOpenDashboard,
  onOpenReport,
  onOpenObject,
}: AlarmBarOverlayProps) {
  if (!enabled || alarms.length === 0) {
    return null;
  }

  return (
    <div className={`operator-alarm-bar-stack operator-alarm-bar-stack--${position}`}>
      {alarms.map((alarm, index) => (
        <AlarmBarItem
          key={alarm.id}
          alarm={alarm}
          showMute={index === 0}
          muted={muted}
          toggleMute={toggleMute}
          onDismiss={() => onDismiss(alarm.id)}
          onOpenDashboard={() => onOpenDashboard(alarm)}
          onOpenReport={() => onOpenReport(alarm)}
          onOpenObject={() => onOpenObject(alarm)}
        />
      ))}
    </div>
  );
}
