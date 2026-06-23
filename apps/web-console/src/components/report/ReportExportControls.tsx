import { useEffect, useState } from "react";
import type { ReportExportFormat } from "../../api/reports";
import { REPORT_EXPORT_OPTIONS, type ReportExportOption } from "./reportExportOptions";

interface ReportExportControlsProps {
  options?: ReportExportOption[];
  disabled?: boolean;
  busy?: boolean;
  className?: string;
  size?: "default" | "sm";
  onExport: (format: ReportExportFormat) => void | Promise<void>;
}

export default function ReportExportControls({
  options = REPORT_EXPORT_OPTIONS,
  disabled = false,
  busy = false,
  className,
  size = "default",
  onExport,
}: ReportExportControlsProps) {
  const [format, setFormat] = useState<ReportExportFormat>(options[0]?.value ?? "xlsx");

  useEffect(() => {
    if (!options.some((option) => option.value === format)) {
      setFormat(options[0]?.value ?? "xlsx");
    }
  }, [options, format]);

  if (options.length === 0) {
    return null;
  }

  const selected = options.find((option) => option.value === format) ?? options[0];

  return (
    <div
      className={[
        "report-export-controls",
        size === "sm" ? "report-export-controls-sm" : "",
        className ?? "",
      ]
        .filter(Boolean)
        .join(" ")}
    >
      <select
        className="report-export-format"
        value={format}
        disabled={disabled || busy}
        aria-label="Формат выгрузки"
        title={selected.title}
        onChange={(event) => setFormat(event.target.value as ReportExportFormat)}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value} title={option.title}>
            {option.label}
          </option>
        ))}
      </select>
      <button
        type="button"
        className={size === "sm" ? "btn btn-sm" : "btn"}
        disabled={disabled || busy}
        title={selected.title}
        onClick={() => void onExport(format)}
      >
        {busy ? "Выгрузка…" : "Выгрузить"}
      </button>
    </div>
  );
}
