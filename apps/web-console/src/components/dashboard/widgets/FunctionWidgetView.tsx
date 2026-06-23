import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { invokeFunction } from "../../../api";
import type { DataRecord } from "../../../types";
import type { FunctionWidget } from "../../../types/dashboard";
import { parseFunctionInputJson, resolveWidgetPath } from "../dashboardUtils";
import { useDashboardContext } from "../DashboardContext";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface FunctionWidgetViewProps {
  widget: FunctionWidget;
  editable?: boolean;
}

export default function FunctionWidgetView({ widget, editable }: FunctionWidgetViewProps) {
  const { t } = useTranslation("widgets");
  const styles = useWidgetStyles(widget.stylesJson);
  const { selection } = useDashboardContext();
  const queryClient = useQueryClient();
  const objectPath = resolveWidgetPath(widget.objectPath, widget.selectionKey, selection);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () => {
      if (!objectPath || !widget.functionName) {
        throw new Error(t("error.objectAndFunctionRequired"));
      }
      let input: DataRecord | undefined;
      if (widget.inputJson?.trim()) {
        input = parseFunctionInputJson(widget.inputJson);
      }
      return invokeFunction(objectPath, widget.functionName, input);
    },
    onSuccess: (result) => {
      const row = result.rows?.[0];
      if (row?.success === false) {
        setError(String(row.message ?? t("view.errorGeneric")));
        setMessage(null);
        return;
      }
      const text = row?.message ? String(row.message) : t("view.done");
      setMessage(text);
      setError(null);
      queryClient.invalidateQueries({ queryKey: ["variables"] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      queryClient.invalidateQueries({ queryKey: ["events"] });
    },
    onError: (err: Error) => {
      setError(err.message);
      setMessage(null);
    },
  });

  const handleClick = () => {
    if (editable) return;
    if (widget.confirmMessage && !window.confirm(widget.confirmMessage)) {
      return;
    }
    mutation.mutate();
  };

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget function-widget"
      editable={editable}
    >
      <button
        type="button"
        className="btn primary function-widget-btn"
        style={styles.value}
        disabled={editable || mutation.isPending || !objectPath || !widget.functionName}
        onClick={handleClick}
      >
        {mutation.isPending ? "…" : widget.buttonLabel ?? widget.functionName}
      </button>
      {message && <p className="function-widget-msg ok">{message}</p>}
      {error && <p className="function-widget-msg error">{error}</p>}
    </DashWidgetShell>
  );
}
