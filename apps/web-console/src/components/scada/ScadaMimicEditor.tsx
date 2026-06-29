import { useCallback, useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import type { MimicConnection, MimicElement, ScadaMimicDocument } from "../../types/scadaMimic";
import {
  createMimicId,
  DEFAULT_LAYER_ID,
  mimicDocumentToJson,
  parseMimicDocument,
  snapCanvasCoordinate,
} from "../../scada/document";
import { findNearestPort, getElementPortPosition, rerouteConnectionsForElement, routeOrthogonal } from "../../scada/connectionRouting";
import { collectBindingPaths, resolveDocumentBindings } from "../../scada/bindingResolver";
import { getSymbol } from "../../scada/symbols/registry";
import { useVariablesBatchQuery } from "../../hooks/useVariablesQuery";
import { useDashboardContext } from "../dashboard/DashboardContext";
import ScadaMimicCanvas from "./ScadaMimicCanvas";
import SymbolPalette from "./SymbolPalette";
import MimicPropertiesPanel from "./MimicPropertiesPanel";

export type ScadaEditorTool = "select" | "place" | "connect";

interface ScadaMimicEditorProps {
  diagramJson: string;
  onSave: (diagramJson: string) => void;
  onClose: () => void;
}

export default function ScadaMimicEditor({ diagramJson, onSave, onClose }: ScadaMimicEditorProps) {
  const { t } = useTranslation("scada");
  const session = useDashboardContext();
  const [document, setDocument] = useState<ScadaMimicDocument>(() => parseMimicDocument(diagramJson));
  const [tool, setTool] = useState<ScadaEditorTool>("select");
  const [placeSymbolId, setPlaceSymbolId] = useState<string | null>("tank.vertical");
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [selectedConnectionId, setSelectedConnectionId] = useState<string | null>(null);
  const [connectFrom, setConnectFrom] = useState<{ elementId: string; port: string } | null>(null);
  const [history, setHistory] = useState<ScadaMimicDocument[]>([]);
  const [historyIndex, setHistoryIndex] = useState(-1);
  const [importText, setImportText] = useState("");

  const pushHistory = useCallback((next: ScadaMimicDocument) => {
    setHistory((h) => [...h.slice(0, historyIndex + 1), next]);
    setHistoryIndex((i) => i + 1);
  }, [historyIndex]);

  const updateDocument = useCallback((updater: (doc: ScadaMimicDocument) => ScadaMimicDocument, recordHistory = true) => {
    setDocument((current) => {
      const next = updater(current);
      if (recordHistory) pushHistory(next);
      return next;
    });
  }, [pushHistory]);

  const selectedElement = useMemo(
    () => document.elements.find((el) => selectedIds.has(el.id)) ?? null,
    [document.elements, selectedIds]
  );
  const selectedConnection = useMemo(
    () => document.connections.find((c) => c.id === selectedConnectionId) ?? null,
    [document.connections, selectedConnectionId]
  );

  const bindingPaths = useMemo(
    () => collectBindingPaths(document.elements, document.connections),
    [document]
  );
  const variablesBatch = useVariablesBatchQuery(bindingPaths, 3000, bindingPaths.length > 0);
  const resolvedBindings = useMemo(
    () => resolveDocumentBindings(document.elements, document.connections, session, variablesBatch.data ?? {}),
    [document, session, variablesBatch.data]
  );

  useEffect(() => {
    setDocument(parseMimicDocument(diagramJson));
    setHistory([]);
    setHistoryIndex(-1);
    setSelectedIds(new Set());
    setSelectedConnectionId(null);
    setConnectFrom(null);
  }, [diagramJson]);

  const handleCanvasClick = (x: number, y: number) => {
    const sx = snapCanvasCoordinate(x, document.grid);
    const sy = snapCanvasCoordinate(y, document.grid);

    if (tool === "place" && placeSymbolId) {
      const symbol = getSymbol(placeSymbolId);
      if (!symbol) return;
      const el: MimicElement = {
        id: createMimicId("el"),
        symbolId: placeSymbolId,
        layerId: DEFAULT_LAYER_ID,
        x: sx,
        y: sy,
        bindings: {},
        props: {},
      };
      updateDocument((doc) => ({ ...doc, elements: [...doc.elements, el] }));
      setSelectedIds(new Set([el.id]));
      setSelectedConnectionId(null);
      return;
    }

    if (tool === "connect") {
      const hit = findNearestPort(document.elements, sx, sy);
      if (!hit) {
        setConnectFrom(null);
        return;
      }
      if (!connectFrom) {
        setConnectFrom({ elementId: hit.element.id, port: hit.port.id });
        return;
      }
      if (connectFrom.elementId === hit.element.id) {
        setConnectFrom(null);
        return;
      }
      const fromEl = document.elements.find((e) => e.id === connectFrom.elementId);
      const toEl = hit.element;
      if (!fromEl) return;
      const fromPos = getElementPortPosition(fromEl, connectFrom.port);
      if (!fromPos) return;
      const points = routeOrthogonal(fromPos.x, fromPos.y, hit.x, hit.y);
      const conn: MimicConnection = {
        id: createMimicId("conn"),
        layerId: DEFAULT_LAYER_ID,
        from: connectFrom,
        to: { elementId: toEl.id, port: hit.port.id },
        points,
      };
      updateDocument((doc) => ({ ...doc, connections: [...doc.connections, conn] }));
      setConnectFrom(null);
      setSelectedConnectionId(conn.id);
      setSelectedIds(new Set());
      return;
    }

    setSelectedIds(new Set());
    setSelectedConnectionId(null);
  };

  const handleElementDrag = (id: string, x: number, y: number) => {
    const sx = snapCanvasCoordinate(x, document.grid);
    const sy = snapCanvasCoordinate(y, document.grid);
    updateDocument((doc) => {
      const elements = doc.elements.map((el) => (el.id === id ? { ...el, x: sx, y: sy } : el));
      const connections = rerouteConnectionsForElement(id, doc.connections, elements);
      return { ...doc, elements, connections };
    }, false);
  };

  const handleDeleteSelected = () => {
    if (selectedConnectionId) {
      updateDocument((doc) => ({
        ...doc,
        connections: doc.connections.filter((c) => c.id !== selectedConnectionId),
      }));
      setSelectedConnectionId(null);
      return;
    }
    if (selectedIds.size === 0) return;
    updateDocument((doc) => ({
      ...doc,
      elements: doc.elements.filter((el) => !selectedIds.has(el.id)),
      connections: doc.connections.filter(
        (c) => !selectedIds.has(c.from.elementId) && !selectedIds.has(c.to.elementId)
      ),
    }));
    setSelectedIds(new Set());
  };

  const undo = () => {
    if (historyIndex <= 0) return;
    const nextIndex = historyIndex - 1;
    setHistoryIndex(nextIndex);
    setDocument(history[nextIndex]);
  };

  const redo = () => {
    if (historyIndex >= history.length - 1) return;
    const nextIndex = historyIndex + 1;
    setHistoryIndex(nextIndex);
    setDocument(history[nextIndex]);
  };

  useEffect(() => {
    globalThis.document.body.classList.add("scada-mimic-editor-open");
    return () => {
      globalThis.document.body.classList.remove("scada-mimic-editor-open");
    };
  }, []);

  const overlay = (
    <div className="scada-mimic-editor-overlay">
      <div className="scada-mimic-editor">
        <header className="scada-mimic-editor-toolbar">
          <div className="scada-toolbar-group">
            <button type="button" className={tool === "select" ? "active" : ""} onClick={() => setTool("select")}>{t("tools.select")}</button>
            <button type="button" className={tool === "place" ? "active" : ""} onClick={() => setTool("place")}>{t("tools.place")}</button>
            <button type="button" className={tool === "connect" ? "active" : ""} onClick={() => setTool("connect")}>{t("tools.connect")}</button>
          </div>
          <div className="scada-toolbar-group">
            <button type="button" onClick={undo} disabled={historyIndex <= 0}>{t("tools.undo")}</button>
            <button type="button" onClick={redo} disabled={historyIndex >= history.length - 1}>{t("tools.redo")}</button>
            <button type="button" onClick={handleDeleteSelected}>{t("tools.delete")}</button>
          </div>
          <div className="scada-toolbar-group scada-toolbar-spacer" />
          <button type="button" onClick={() => onSave(mimicDocumentToJson(document))}>{t("tools.save")}</button>
          <button type="button" onClick={onClose}>{t("tools.close")}</button>
        </header>
        <div className="scada-mimic-editor-body">
          <aside className="scada-mimic-editor-sidebar">
            <SymbolPalette
              selectedSymbolId={placeSymbolId}
              onSelectSymbol={(id) => {
                setPlaceSymbolId(id);
                setTool("place");
              }}
            />
          </aside>
          <main className="scada-mimic-editor-canvas-wrap">
            <ScadaMimicCanvas
              document={document}
              valuesByElementId={resolvedBindings.byElementId}
              valuesByConnectionId={resolvedBindings.byConnectionId}
              editMode
              selectedIds={selectedIds}
              selectedConnectionId={selectedConnectionId}
              onSelectElement={(id, additive) => {
                setSelectedConnectionId(null);
                setSelectedIds((prev) => {
                  if (additive) {
                    const next = new Set(prev);
                    if (next.has(id)) next.delete(id);
                    else next.add(id);
                    return next;
                  }
                  return new Set([id]);
                });
              }}
              onSelectConnection={setSelectedConnectionId}
              onCanvasClick={handleCanvasClick}
              onElementDrag={handleElementDrag}
            />
          </main>
          <aside className="scada-mimic-editor-props">
            <MimicPropertiesPanel
              document={document}
              selectedElement={selectedElement}
              selectedConnection={selectedConnection}
              onUpdateElement={(el) =>
                updateDocument((doc) => ({
                  ...doc,
                  elements: doc.elements.map((item) => (item.id === el.id ? el : item)),
                }))
              }
              onUpdateConnection={(conn) =>
                updateDocument((doc) => ({
                  ...doc,
                  connections: doc.connections.map((item) => (item.id === conn.id ? conn : item)),
                }))
              }
              onDeleteSelected={handleDeleteSelected}
            />
            <details className="scada-import-export">
              <summary>{t("importExport.title")}</summary>
              <textarea rows={6} value={importText} onChange={(e) => setImportText(e.target.value)} placeholder={t("importExport.placeholder")} />
              <div className="scada-import-export-actions">
                <button type="button" onClick={() => setImportText(mimicDocumentToJson(document))}>{t("importExport.export")}</button>
                <button
                  type="button"
                  onClick={() => {
                    setDocument(parseMimicDocument(importText));
                    pushHistory(parseMimicDocument(importText));
                  }}
                >
                  {t("importExport.import")}
                </button>
              </div>
            </details>
          </aside>
        </div>
      </div>
    </div>
  );

  return createPortal(overlay, globalThis.document.body);
}
