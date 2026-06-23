import type { ReportExportFormat } from "../../api/reports";

export interface ReportExportOption {
  value: ReportExportFormat;
  label: string;
  title?: string;
}

export const REPORT_EXPORT_OPTIONS: ReportExportOption[] = [
  {
    value: "xlsx",
    label: "XLSX",
    title: "Таблица Excel; с шаблоном .xls/.xlsx — оформление YARG",
  },
  {
    value: "csv",
    label: "CSV",
    title: "Текстовая таблица (разделитель — запятая)",
  },
  {
    value: "html",
    label: "HTML",
    title: "HTML-таблица для просмотра в браузере",
  },
  {
    value: "pdf",
    label: "PDF",
    title: "PDF через YARG-шаблон (.xls, .docx) или таблица, если шаблон не подошёл",
  },
];

export function filterReportExportOptions(
  enabled: (format: ReportExportFormat) => boolean
): ReportExportOption[] {
  return REPORT_EXPORT_OPTIONS.filter((option) => enabled(option.value));
}
