import { useEffect, useMemo, useRef, useState } from "react";
import type { SheetConfig, SheetMode } from "../../../types/dashboard";
import {
  createSheetFormulaEngine,
  setIspfFormulaContext,
  type IspfFormulaContext,
  type SheetFormulaEngine,
  type SheetValues,
} from "./sheetFormulaEngine";

export interface UseSheetFormulaEngineOptions {
  config: SheetConfig;
  mode: SheetMode;
  contents: SheetValues;
  externalByAddr?: Map<string, number | string | boolean>;
  ispfContext?: IspfFormulaContext;
}

export interface UseSheetFormulaEngineResult {
  getCellValue: (address: string) => unknown;
  getCellEditContent: (address: string) => string;
  setCellContent: (address: string, raw: string) => void;
  setInputValue: (address: string, raw: string) => void;
  setExternalValue: (address: string, value: number | string | boolean) => void;
  collectCellContents: () => SheetValues;
  collectInputValues: () => SheetValues;
  isBindingCell: (address: string) => boolean;
  copySelection: () => void;
  pasteAt: (address: string) => void;
  undo: () => boolean;
  redo: () => boolean;
  exportCsv: () => string;
  setSelectionAnchor: (address: string | null) => void;
  mergeRegionContents: (region: SheetValues) => void;
  revision: number;
}

export function useSheetFormulaEngine({
  config,
  mode,
  contents,
  externalByAddr,
  ispfContext,
}: UseSheetFormulaEngineOptions): UseSheetFormulaEngineResult {
  const engineRef = useRef<SheetFormulaEngine | null>(null);
  const engineInputsRef = useRef<{
    configKey: string;
    contentsKey: string;
    mode: SheetMode;
  } | null>(null);
  const [revision, setRevision] = useState(0);
  const contentsKey = useMemo(() => JSON.stringify(contents), [contents]);
  const externalKey = useMemo(
    () => JSON.stringify(externalByAddr ? Array.from(externalByAddr.entries()) : []),
    [externalByAddr]
  );
  const configKey = useMemo(() => JSON.stringify(config), [config]);
  const ispfContextKey = useMemo(
    () =>
      JSON.stringify(
        ispfContext
          ? [
              [...ispfContext.bindingValues.entries()],
              [...ispfContext.tableColumnSums.entries()],
              [...ispfContext.histValues.entries()],
            ]
          : null
      ),
    [ispfContext]
  );

  if (ispfContext) {
    setIspfFormulaContext(ispfContext);
  }

  const engineContentsKey = engineRef.current
    ? JSON.stringify(engineRef.current.collectCellContents())
    : null;
  const previousInputs = engineInputsRef.current;
  const shouldRecreateEngine =
    !engineRef.current ||
    !previousInputs ||
    previousInputs.configKey !== configKey ||
    previousInputs.mode !== mode ||
    (previousInputs.contentsKey !== contentsKey && engineContentsKey !== contentsKey);

  if (shouldRecreateEngine) {
    engineRef.current?.destroy();
    engineRef.current = createSheetFormulaEngine(config, mode, contents, externalByAddr);
    engineInputsRef.current = { configKey, contentsKey, mode };
  } else if (previousInputs?.contentsKey !== contentsKey) {
    engineInputsRef.current = { configKey, contentsKey, mode };
  }

  useEffect(() => {
    return () => {
      engineRef.current?.destroy();
      engineRef.current = null;
    };
  }, []);

  useEffect(() => {
    if (!ispfContext) {
      return;
    }
    setIspfFormulaContext(ispfContext);
    engineRef.current?.refreshComputed();
    setRevision((n) => n + 1);
  }, [ispfContextKey, ispfContext]);

  useEffect(() => {
    if (!engineRef.current || !externalByAddr) {
      return;
    }
    for (const [addr, value] of externalByAddr) {
      engineRef.current.setExternalValue(addr, value);
    }
    setRevision((n) => n + 1);
  }, [externalKey, externalByAddr]);

  const bump = () => setRevision((n) => n + 1);

  return {
    getCellValue: (address: string) => engineRef.current?.getCellValue(address) ?? "",
    getCellEditContent: (address: string) => engineRef.current?.getCellEditContent(address) ?? "",
    setCellContent: (address: string, raw: string) => {
      engineRef.current?.setCellContent(address, raw);
      bump();
    },
    setInputValue: (address: string, raw: string) => {
      engineRef.current?.setInputValue(address, raw);
      bump();
    },
    setExternalValue: (address: string, value: number | string | boolean) => {
      engineRef.current?.setExternalValue(address, value);
      bump();
    },
    collectCellContents: () => engineRef.current?.collectCellContents() ?? {},
    collectInputValues: () => engineRef.current?.collectInputValues() ?? {},
    isBindingCell: (address: string) => engineRef.current?.isBindingCell(address) ?? false,
    copySelection: () => engineRef.current?.copySelection(),
    pasteAt: (address: string) => {
      engineRef.current?.pasteAt(address);
      bump();
    },
    undo: () => {
      const ok = engineRef.current?.undo() ?? false;
      if (ok) bump();
      return ok;
    },
    redo: () => {
      const ok = engineRef.current?.redo() ?? false;
      if (ok) bump();
      return ok;
    },
    exportCsv: () => engineRef.current?.exportCsv() ?? "",
    setSelectionAnchor: (address: string | null) => {
      engineRef.current?.setSelectionAnchor(address);
    },
    mergeRegionContents: (region: SheetValues) => {
      engineRef.current?.mergeRegionContents(region);
      bump();
    },
    revision,
  };
}
