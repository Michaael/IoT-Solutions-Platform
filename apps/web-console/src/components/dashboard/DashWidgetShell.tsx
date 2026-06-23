import type { CSSProperties, ReactNode } from "react";
import WidgetDragHandle from "./WidgetDragHandle";
import { useWidgetStyles } from "./widgetStyles";

interface DashWidgetShellProps {
  title: ReactNode;
  stylesJson?: string;
  className: string;
  editable?: boolean;
  demo?: boolean;
  rootStyle?: CSSProperties;
  footer?: ReactNode;
  headerExtra?: ReactNode;
  children: ReactNode;
}

export default function DashWidgetShell({
  title,
  stylesJson,
  className,
  editable = false,
  demo = false,
  rootStyle,
  footer,
  headerExtra,
  children,
}: DashWidgetShellProps) {
  const styles = useWidgetStyles(stylesJson);

  return (
    <div className={className} style={{ ...styles.card, ...rootStyle }}>
      <WidgetDragHandle visible={editable} />
      <div className="dash-widget-title" style={styles.title}>
        {title}
        {demo ? <span className="dash-widget-demo-badge">пример</span> : null}
        {headerExtra}
      </div>
      {children}
      {footer != null ? (
        <div className="dash-widget-meta mono" style={styles.meta}>
          {footer}
        </div>
      ) : null}
    </div>
  );
}
