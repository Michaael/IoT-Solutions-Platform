import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from "react";
import { useTranslation } from "react-i18next";
import { useVirtualizer } from "@tanstack/react-virtual";
import type { SpreadsheetWidget } from "../../../types/dashboard";
import { useWidgetObjectPath } from "../../../hooks/useWidgetObjectPath";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";
import { rowColToA1 } from "../sheet/sheetAddress";
import { formatSheetCellValue } from "../sheet/sheetFormat";
import { rowPassesColumnFilters } from "../sheet/sheetFilters";
import { useSheetFormulaEngine } from "../sheet/useSheetFormulaEngine";
import { useSheetBindings } from "../sheet/useSheetBindings";
import { useSpreadsheetPersist } from "../sheet/useSpreadsheetPersist";
import { validateSheetCellValue } from "../sheet/sheetValidation";

const VIRTUALIZE_ROW_THRESHOLD = 50;
const TABLE_ROW_ESTIMATE_PX = 36;

interface SpreadsheetConfiguredGridViewProps {
  widget: SpreadsheetWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function SpreadsheetConfiguredGridView({
  widget,
  refreshIntervalMs,
  editable: editMode,
}: SpreadsheetConfiguredGridViewProps) {
  const { t } = useTranslation(["widgets", "common"]);
  const styles = useWidgetStyles(widget.stylesJson);
  const tableWrapRef = useRef<HTMLDivElement>(null);
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey);

  const {
    sheetConfig,
    localContents,
    setLocalContents,
    schedulePersist,
    registerPersistSnapshot,
    isLoading,
    canEdit: widgetEditable,
    persistWarning,
  } = useSpreadsheetPersist(widget, objectPath, refreshIntervalMs);

  const [selectedCell, setSelectedCell] = useState<string | null>(null);
  const [validationErrors, setValidationErrors] = useState<Record<string, string>>({});
  const [columnFilterDraft, setColumnFilterDraft] = useState<Record<string, string>>({});

  const { externalByAddr, ispfContext } = useSheetBindings(
    sheetConfig,
    objectPath,
    refreshIntervalMs,
    localContents
  );

  const formula = useSheetFormulaEngine({
    config: sheetConfig,
    mode: "configured",
    contents: localContents,
    externalByAddr,
    ispfContext,
  });

  const formulaRef = useRef(formula);
  formulaRef.current = formula;

  useEffect(
    () =>
      registerPersistSnapshot(() => ({
        contents: formulaRef.current.collectCellContents(),
        meta: null,
      })),
    [registerPersistSnapshot]
  );

  const handleInputBlur = (address: string, raw: string) => {
    const cell = sheetConfig.cells[address];
    const validation = validateSheetCellValue(raw, cell?.validation);
    if (!validation.valid) {
      setValidationErrors((prev) => ({
        ...prev,
        [address]: validation.message ?? t("spreadsheet.validationError"),
      }));
      return;
    }
    setValidationErrors((prev) => {
      const next = { ...prev };
      delete next[address];
      return next;
    });
    const nextContents = { ...localContents, [address]: raw };
    setLocalContents(nextContents);
    formula.setInputValue(address, raw);
    if (!editMode && widgetEditable) {
      schedulePersist(nextContents);
    }
  };

  const inputAddresses = useMemo(
    () =>
      Object.entries(sheetConfig.cells)
        .filter(([, cell]) => cell.kind === "input")
        .map(([addr]) => addr),
    [sheetConfig.cells]
  );

  const focusNextInput = (current: string, reverse = false) => {
    const idx = inputAddresses.indexOf(current);
    if (idx < 0) {
      return;
    }
    const nextIdx = reverse ? idx - 1 : idx + 1;
    const next = inputAddresses[nextIdx];
    if (next) {
      const el = tableWrapRef.current?.querySelector<HTMLInputElement>(
        `input[data-cell="${next}"]`
      );
      el?.focus();
    }
  };

  const onInputKeyDown = (address: string, event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Enter") {
      event.preventDefault();
      focusNextInput(address);
    } else if (event.key === "Tab" && !event.shiftKey) {
      event.preventDefault();
      focusNextInput(address);
    } else if (event.key === "Tab" && event.shiftKey) {
      event.preventDefault();
      focusNextInput(address, true);
    }
  };

  const colLabels =
    sheetConfig.colLabels ??
    Array.from({ length: sheetConfig.cols }, (_, i) =>
      rowColToA1(0, i).replace(/\d+$/, "")
    );

  const getCellText = (address: string): string => {
    const cell = sheetConfig.cells[address];
    if (!cell) {
      return "";
    }
    if (cell.kind === "label") {
      return cell.text ?? "";
    }
    const raw =
      cell.kind === "input"
        ? (localContents[address] ?? cell.default ?? "")
        : formula.getCellValue(address);
    return formatSheetCellValue(raw, cell.format);
  };

  const visibleRows = useMemo(() => {
    const filters = sheetConfig.columnFilters ?? [];
    const activeFilters = filters.length
      ? filters
      : Object.entries(columnFilterDraft)
          .filter(([, v]) => v.trim())
          .map(([column, value]) => ({ column, value }));

    const configWithFilters = { ...sheetConfig, columnFilters: activeFilters };
    return Array.from({ length: sheetConfig.rows }, (_, rowIndex) => rowIndex).filter(
      (rowIndex) =>
        rowPassesColumnFilters(rowIndex, configWithFilters, (addr) => getCellText(addr))
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps -- revision drives formula recalc
  }, [sheetConfig, columnFilterDraft, formula.revision, localContents]);

  const shouldVirtualize = visibleRows.length >= VIRTUALIZE_ROW_THRESHOLD;
  const rowVirtualizer = useVirtualizer({
    count: visibleRows.length,
    getScrollElement: () => tableWrapRef.current,
    estimateSize: () => TABLE_ROW_ESTIMATE_PX,
    overscan: 10,
    enabled: shouldVirtualize,
  });

  const virtualRows = shouldVirtualize ? rowVirtualizer.getVirtualItems() : [];
  const paddingTop = virtualRows.length > 0 ? virtualRows[0].start : 0;
  const paddingBottom =
    virtualRows.length > 0
      ? rowVirtualizer.getTotalSize() - virtualRows[virtualRows.length - 1].end
      : 0;

  const canEdit = !editMode && widgetEditable;

  const selectedCellConfig = selectedCell ? sheetConfig.cells[selectedCell] : undefined;
  const formulaBarText =
    selectedCellConfig?.kind === "formula"
      ? (selectedCellConfig.expr ?? "")
      : selectedCellConfig?.kind === "input"
        ? (localContents[selectedCell ?? ""] ?? selectedCellConfig.default ?? "")
        : selectedCell
          ? getCellText(selectedCell)
          : "";

  const renderCell = (rowIndex: number, colIndex: number) => {
    const address = rowColToA1(rowIndex, colIndex);
    const cell = sheetConfig.cells[address];
    const cellStyle = cell?.style;

    if (!cell) {
      return (
        <td
          key={address}
          className="dash-sheet-cell dash-sheet-cell-empty"
          onClick={() => setSelectedCell(address)}
        />
      );
    }

    const isSelected = selectedCell === address;
    const hasError = Boolean(validationErrors[address]);

    if (cell.kind === "label") {
      return (
        <td
          key={address}
          className={`dash-sheet-cell dash-sheet-label${isSelected ? " dash-sheet-selected" : ""}`}
          style={cellStyle}
          onClick={() => setSelectedCell(address)}
        >
          {cell.text ?? ""}
        </td>
      );
    }

    if (cell.kind === "input" && canEdit) {
      return (
        <td
          key={address}
          className={`dash-sheet-cell dash-sheet-input${isSelected ? " dash-sheet-selected" : ""}${hasError ? " dash-sheet-invalid" : ""}`}
          style={cellStyle}
          title={validationErrors[address]}
        >
          <input
            data-cell={address}
            defaultValue={localContents[address] ?? cell.default ?? ""}
            key={`${address}-${formula.revision}-${localContents[address] ?? ""}`}
            onFocus={() => setSelectedCell(address)}
            onBlur={(e) => handleInputBlur(address, e.target.value)}
            onKeyDown={(e) => onInputKeyDown(address, e)}
          />
        </td>
      );
    }

    const display = getCellText(address);
    const alignRight = cell.format?.type === "number" || cell.kind === "formula";

    return (
      <td
        key={address}
        className={`dash-sheet-cell dash-sheet-${cell.kind}${isSelected ? " dash-sheet-selected" : ""}${alignRight ? " dash-sheet-num" : ""}`}
        style={cellStyle}
        onClick={() => setSelectedCell(address)}
      >
        {display || "—"}
      </td>
    );
  };

  const renderRow = (rowIndex: number) => (
    <tr key={rowIndex}>
      <th className="dash-sheet-row-header">{rowIndex + 1}</th>
      {Array.from({ length: sheetConfig.cols }, (_, colIndex) => renderCell(rowIndex, colIndex))}
    </tr>
  );

  const showColumnFilters = (sheetConfig.columnFilters?.length ?? 0) > 0;

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-spreadsheet"
      editable={editMode}
    >
      {isLoading ? (
        <p className="hint">{t("common:action.loading")}</p>
      ) : (
        <>
          {persistWarning ? (
            <div className="dash-sheet-import-notice dash-sheet-persist-warning" role="status">
              {t(persistWarning, {
                name: widget.valuesVariable ?? "",
                objectPath: objectPath || (widget.objectPath ?? ""),
              })}
            </div>
          ) : null}
          <div className="dash-sheet-formula-bar">
            <span className="dash-sheet-formula-label">{selectedCell ?? ""}</span>
            <input
              className="dash-sheet-formula-input"
              readOnly
              value={formulaBarText}
              placeholder={t("spreadsheet.formulaBar")}
            />
          </div>
          {showColumnFilters && (
            <div className="dash-sheet-filters">
              {colLabels.map((col) => (
                <label key={col} className="dash-sheet-filter">
                  {col}
                  <input
                    value={columnFilterDraft[col] ?? ""}
                    onChange={(e) =>
                      setColumnFilterDraft((prev) => ({ ...prev, [col]: e.target.value }))
                    }
                    placeholder={t("spreadsheet.filterPlaceholder")}
                  />
                </label>
              ))}
            </div>
          )}
          <div className="dash-table-wrap dash-sheet-wrap" ref={tableWrapRef} style={styles.body}>
            <table className="dash-object-table dash-sheet-table">
              <thead>
                <tr>
                  <th className="dash-sheet-corner" />
                  {colLabels.map((col) => (
                    <th key={col} className="dash-sheet-col-header">
                      {col}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {shouldVirtualize && paddingTop > 0 && (
                  <tr aria-hidden="true" className="dash-table-spacer">
                    <td
                      colSpan={sheetConfig.cols + 1}
                      style={{ height: paddingTop, padding: 0, border: 0 }}
                    />
                  </tr>
                )}
                {shouldVirtualize
                  ? virtualRows.map((virtualRow) => renderRow(visibleRows[virtualRow.index]))
                  : visibleRows.map((rowIndex) => renderRow(rowIndex))}
                {shouldVirtualize && paddingBottom > 0 && (
                  <tr aria-hidden="true" className="dash-table-spacer">
                    <td
                      colSpan={sheetConfig.cols + 1}
                      style={{ height: paddingBottom, padding: 0, border: 0 }}
                    />
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </>
      )}
    </DashWidgetShell>
  );
}
