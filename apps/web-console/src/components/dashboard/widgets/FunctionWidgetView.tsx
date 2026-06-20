import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { invokeFunction } from "../../../api";
import type { FunctionWidget } from "../../../types/dashboard";
import { resolveWidgetPath } from "../dashboardUtils";
import { useDashboardContext } from "../DashboardContext";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface FunctionWidgetViewProps {
  widget: FunctionWidget;
  editable?: boolean;
}

export default function FunctionWidgetView({ widget, editable }: FunctionWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);
  const { selection } = useDashboardContext();
  const queryClient = useQueryClient();
  const objectPath = resolveWidgetPath(widget.objectPath, widget.selectionKey, selection);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () => {
      if (!objectPath || !widget.functionName) {
        throw new Error("Объект и функция обязательны");
      }
      let input: unknown;
      if (widget.inputJson?.trim()) {
        try {
          input = JSON.parse(widget.inputJson);
        } catch {
          throw new Error("Некорректный inputJson");
        }
      }
      return invokeFunction(objectPath, widget.functionName, input);
    },
    onSuccess: (result) => {
      const row = result.rows?.[0];
      const text = row?.message ? String(row.message) : "Выполнено";
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
