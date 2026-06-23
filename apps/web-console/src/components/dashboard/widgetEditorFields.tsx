import { Children, Fragment, isValidElement, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import { useQuery } from "@tanstack/react-query";
import { fetchReport } from "../../api/reports";
import type { DashboardWidget } from "../../types/dashboard";
import { WIDGET_HISTORY_RANGE_OPTIONS } from "../../types/dashboard";
import {
  DATA_BINDING_HINTS,
  widgetDataBinding,
  WIDGET_TYPE_HINTS,
} from "./widgetEditorBinding";

type ObjectOption = { path: string; displayName: string; variableNames: string[] };
type DashboardOption = { path: string; displayName: string };
type ReportOption = { path: string; displayName: string };

export interface WidgetFieldContext {
  widget: DashboardWidget;
  objects: ObjectOption[];
  dashboards: DashboardOption[];
  reports: ReportOption[];
  variables: string[];
  variableSelectEnabled: boolean;
  update: (patch: Partial<DashboardWidget>) => void;
}

function Section({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className="widget-editor-section-block">
      <h5 className="widget-editor-section">{title}</h5>
      {hint && <p className="hint widget-editor-hint">{hint}</p>}
    </div>
  );
}

function flattenFieldChildren(children: ReactNode): ReactNode[] {
  const out: ReactNode[] = [];
  Children.forEach(children, (child) => {
    if (child == null || child === false) return;
    if (isValidElement<{ children?: ReactNode }>(child) && child.type === Fragment) {
      out.push(...flattenFieldChildren(child.props.children));
      return;
    }
    if (isValidElement<{ children?: ReactNode }>(child) && child.type === FieldPairs) {
      out.push(...flattenFieldChildren(child.props.children));
      return;
    }
    out.push(child);
  });
  return out;
}

function isFullWidthField(node: ReactNode): boolean {
  if (!isValidElement<{ className?: string }>(node)) return false;
  if (node.type === FormRow) return true;
  if (node.type === Section) return true;
  const className = node.props.className;
  if (typeof className === "string") {
    if (className.includes("full") || className.includes("widget-editor-section-block")) return true;
  }
  if (node.type === "h5" || node.type === "p") return true;
  return false;
}

export function FieldPairs({ children }: { children: ReactNode }) {
  const items = flattenFieldChildren(children);
  const result: ReactNode[] = [];
  let pair: ReactNode[] = [];

  const flushPair = () => {
    if (pair.length === 0) return;
    result.push(
      <FormRow key={`row-${result.length}`}>
        {pair[0]}
        {pair[1] ?? <div className="form-grid-row-spacer" aria-hidden="true" />}
      </FormRow>,
    );
    pair = [];
  };

  for (const item of items) {
    if (isFullWidthField(item)) {
      flushPair();
      result.push(item);
    } else {
      pair.push(item);
      if (pair.length === 2) flushPair();
    }
  }
  flushPair();

  return <>{result}</>;
}

function FormRow({ children }: { children: ReactNode }) {
  return <div className="form-grid-row">{children}</div>;
}

function StackedSlot({ children }: { children: ReactNode }) {
  return <div className="field-controls-slot field-controls-slot--stacked">{children}</div>;
}

function FieldLabel({
  caption,
  children,
  className,
}: {
  caption: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <label className={className}>
      <span className="field-caption">{caption}</span>
      {children}
    </label>
  );
}

function PathSelect({
  label,
  value,
  objects,
  onChange,
  placeholder,
}: {
  label: string;
  value: string;
  objects: ObjectOption[];
  onChange: (path: string) => void;
  placeholder?: string;
}) {
  const { t } = useTranslation(["widgets", "common"]);
  return (
    <FieldLabel caption={label} className="path-select-field">
      <div className="field-controls">
        <select value={value} onChange={(e) => onChange(e.target.value)}>
          <option value="">—</option>
          {objects.map((ctx) => (
            <option key={ctx.path} value={ctx.path}>
              {ctx.displayName}
            </option>
          ))}
        </select>
        <input
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder ?? t("editor.placeholder.orEnterPath")}
        />
      </div>
    </FieldLabel>
  );
}

function ReportParameterHints({ reportPath }: { reportPath: string }) {
  const { t } = useTranslation(["widgets", "common"]);
  const metaQuery = useQuery({
    queryKey: ["report-widget-hints", reportPath],
    queryFn: () => fetchReport(reportPath),
    enabled: Boolean(reportPath?.trim()),
  });

  if (!reportPath?.trim()) {
    return null;
  }
  if (metaQuery.isLoading) {
    return <p className="hint full">{t("editor.reportSchemaLoading")}</p>;
  }
  if (metaQuery.error || !metaQuery.data) {
    return null;
  }

  const data = metaQuery.data;
  const isTree = data.reportType === "tree-variables";
  if (isTree) {
    return (
      <p className="hint full">
        tree-variables: <code>{data.devicePathPattern}</code> · variable{" "}
        <code>{data.variableName}</code>
        {data.hasTemplate ? t("editor.yargTemplateLoaded") : ""}
      </p>
    );
  }

  const params = data.parameters ?? [];
  const defaults =
    data.defaultParameters && Object.keys(data.defaultParameters).length > 0
      ? JSON.stringify(data.defaultParameters)
      : null;

  return (
    <p className="hint full">
      {params.length > 0 ? (
        <>
          {t("editor.sqlParameters")} <code>{params.join(", ")}</code>
          {defaults && (
            <>
              {" "}
              · defaults: <code>{defaults}</code>
            </>
          )}
        </>
      ) : (
        <>{t("editor.sqlNoParameters")}</>
      )}
      {data.dataSourcePath && (
        <>
          {" "}
          · data source: <code>{data.dataSourcePath}</code>
        </>
      )}
      {data.hasTemplate ? t("editor.yargTemplateLoaded") : ""}
    </p>
  );
}

function rowNavigationFields(ctx: WidgetFieldContext, prefix: "row" | "card", t: TFunction): ReactNode {
  const w = ctx.widget;
  const update = ctx.update;

  if (prefix === "row" && w.type === "map") {
    const mw = w;
    return (
      <>
        <FormRow>
          <DashboardPathField
            caption={t("editor.rowTargetDashboard")}
            value={mw.rowTargetDashboard ?? ""}
            dashboards={ctx.dashboards}
            onChange={(v) => update({ rowTargetDashboard: v || undefined })}
          />
          <FieldLabel caption="rowOpenMode">
            <StackedSlot>
              <select
                value={mw.rowOpenMode ?? "navigate"}
                onChange={(e) => update({ rowOpenMode: e.target.value as "navigate" | "modal" })}
                disabled={!mw.rowTargetDashboard}
              >
                <option value="navigate">navigate</option>
                <option value="modal">modal</option>
              </select>
            </StackedSlot>
          </FieldLabel>
        </FormRow>
        <FormRow>
          <FieldLabel caption="rowSelectionKey">
            <input
              value={mw.rowSelectionKey ?? ""}
              onChange={(e) => update({ rowSelectionKey: e.target.value || undefined })}
              disabled={!mw.rowTargetDashboard}
            />
          </FieldLabel>
          <FieldLabel caption="rowParamsJson">
            <textarea
              rows={2}
              value={mw.rowParamsJson ?? ""}
              onChange={(e) => update({ rowParamsJson: e.target.value || undefined })}
            />
          </FieldLabel>
        </FormRow>
      </>
    );
  }

  if (prefix === "row" && w.type === "object-table") {
    const tw = w;
    return (
      <>
        <FormRow>
          <DashboardPathField
            caption="rowTargetDashboard"
            value={tw.rowTargetDashboard ?? ""}
            dashboards={ctx.dashboards}
            onChange={(v) => update({ rowTargetDashboard: v || undefined })}
          />
          <FieldLabel caption="rowOpenMode">
            <StackedSlot>
              <select
                value={tw.rowOpenMode ?? "navigate"}
                onChange={(e) => update({ rowOpenMode: e.target.value as "navigate" | "modal" })}
                disabled={!tw.rowTargetDashboard}
              >
                <option value="navigate">navigate</option>
                <option value="modal">modal</option>
              </select>
            </StackedSlot>
          </FieldLabel>
        </FormRow>
        <FormRow>
          <FieldLabel caption="rowSelectionKey">
            <input
              value={tw.rowSelectionKey ?? ""}
              onChange={(e) => update({ rowSelectionKey: e.target.value || undefined })}
              disabled={!tw.rowTargetDashboard}
            />
          </FieldLabel>
          <FieldLabel caption="rowParamsJson">
            <textarea
              rows={2}
              value={tw.rowParamsJson ?? ""}
              onChange={(e) => update({ rowParamsJson: e.target.value || undefined })}
            />
          </FieldLabel>
        </FormRow>
      </>
    );
  }

  if (prefix === "card" && w.type === "card-grid") {
    const cw = w;
    return (
      <>
        <FormRow>
          <DashboardPathField
            caption="cardTargetDashboard"
            value={cw.cardTargetDashboard ?? ""}
            dashboards={ctx.dashboards}
            onChange={(v) => update({ cardTargetDashboard: v || undefined })}
          />
          <FieldLabel caption="cardOpenMode">
            <StackedSlot>
              <select
                value={cw.cardOpenMode ?? "navigate"}
                onChange={(e) => update({ cardOpenMode: e.target.value as "navigate" | "modal" })}
                disabled={!cw.cardTargetDashboard}
              >
                <option value="navigate">navigate</option>
                <option value="modal">modal</option>
              </select>
            </StackedSlot>
          </FieldLabel>
        </FormRow>
        <FormRow>
          <FieldLabel caption="cardSelectionKey">
            <input
              value={cw.cardSelectionKey ?? ""}
              onChange={(e) => update({ cardSelectionKey: e.target.value || undefined })}
              disabled={!cw.cardTargetDashboard}
            />
          </FieldLabel>
          <FieldLabel caption="cardParamsJson">
            <textarea
              rows={2}
              value={cw.cardParamsJson ?? ""}
              onChange={(e) => update({ cardParamsJson: e.target.value || undefined })}
            />
          </FieldLabel>
        </FormRow>
      </>
    );
  }

  return null;
}

function DashboardPathField({
  caption,
  value,
  dashboards,
  onChange,
}: {
  caption: string;
  value: string;
  dashboards: DashboardOption[];
  onChange: (path: string) => void;
}) {
  return (
    <FieldLabel caption={caption} className="path-select-field">
      <div className="field-controls">
        <DashboardPathInput value={value} dashboards={dashboards} onChange={onChange} />
      </div>
    </FieldLabel>
  );
}

function DashboardPathInput({
  value,
  dashboards,
  onChange,
}: {
  value: string;
  dashboards: DashboardOption[];
  onChange: (path: string) => void;
}) {
  return (
    <>
      <select value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">—</option>
        {dashboards.map((d) => (
          <option key={d.path} value={d.path}>
            {d.displayName}
          </option>
        ))}
      </select>
      <input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="root.platform.dashboards.detail"
      />
    </>
  );
}

export function WidgetDataSourceFields(ctx: WidgetFieldContext) {
  const { t } = useTranslation(["widgets", "common"]);
  const { widget, objects, variables, variableSelectEnabled, update } = ctx;
  const binding = widgetDataBinding(widget.type);
  const typeHint = WIDGET_TYPE_HINTS[widget.type];

  return (
    <FieldPairs>
      <Section
        title={t("editor.dataSource")}
        hint={[DATA_BINDING_HINTS[binding], typeHint].filter(Boolean).join(" ")}
      />

      {(binding === "object-variable" || binding === "object-only") && (
        <>
          <FormRow>
            <PathSelect
              label={t("editor.objectPath")}
              value={widget.objectPath ?? ""}
              objects={objects}
              onChange={(path) => update({ objectPath: path || undefined, variableName: "" })}
            />
            <FieldLabel caption={t("editor.selectionKey")}>
              <div className="field-controls-slot field-controls-slot--stacked">
                <input
                  value={widget.selectionKey ?? ""}
                  onChange={(e) => update({ selectionKey: e.target.value || undefined })}
                  placeholder={t("editor.placeholder.selectionPath")}
                />
              </div>
            </FieldLabel>
          </FormRow>
          <FormRow>
            <FieldLabel caption={t("editor.contextPathKey")}>
              <input
                value={widget.contextPathKey ?? ""}
                onChange={(e) => update({ contextPathKey: e.target.value || undefined })}
                placeholder={t("editor.placeholder.contextPathEmpty")}
              />
            </FieldLabel>
            <FieldLabel caption={t("editor.modelHintPath")}>
              <select
                value={widget.modelHintPath ?? ""}
                onChange={(e) => update({ modelHintPath: e.target.value || undefined })}
              >
                <option value="">—</option>
                {objects.map((o) => (
                  <option key={o.path} value={o.path}>
                    {o.displayName}
                  </option>
                ))}
              </select>
            </FieldLabel>
          </FormRow>
        </>
      )}

      {binding === "object-variable" && (
        <FormRow>
          <FieldLabel caption={t("editor.variableName")}>
            <div
              className={
                !widget.objectPath && widget.selectionKey
                  ? "field-controls"
                  : "field-controls-slot field-controls-slot--stacked"
              }
            >
              <select
                value={widget.variableName ?? ""}
                onChange={(e) => update({ variableName: e.target.value || undefined })}
                disabled={!variableSelectEnabled}
              >
                <option value="">—</option>
                {variables.map((name) => (
                  <option key={name} value={name}>
                    {name}
                  </option>
                ))}
              </select>
              {!widget.objectPath && widget.selectionKey && (
                <input
                  placeholder={t("editor.placeholder.orEnterVariable")}
                  value={widget.variableName ?? ""}
                  onChange={(e) => update({ variableName: e.target.value || undefined })}
                />
              )}
            </div>
          </FieldLabel>
          <FieldLabel caption={t("editor.valueField")}>
            <div className="field-controls-slot field-controls-slot--stacked">
              <input
                value={widget.valueField ?? "value"}
                onChange={(e) => update({ valueField: e.target.value || undefined })}
                placeholder="value"
              />
            </div>
          </FieldLabel>
        </FormRow>
      )}

      {binding === "parent-catalog" && (
        <FormRow>
          <PathSelect
            label={t("editor.parentPath")}
            value={(widget as { parentPath?: string }).parentPath ?? ""}
            objects={objects}
            onChange={(path) => update({ parentPath: path } as Partial<DashboardWidget>)}
            placeholder="root.platform.devices"
          />
          <FieldLabel caption={t("editor.selectionKeyOnClick")}>
            <StackedSlot>
              <input
                value={widget.selectionKey ?? ""}
                onChange={(e) => update({ selectionKey: e.target.value || undefined })}
                placeholder="device"
              />
            </StackedSlot>
          </FieldLabel>
        </FormRow>
      )}

      {(binding === "session" || widget.paramKey) && (
        <label>
          {t("editor.paramKey")}
          <input
            value={widget.paramKey ?? ""}
            onChange={(e) => update({ paramKey: e.target.value || undefined })}
          />
        </label>
      )}
    </FieldPairs>
  );
}

function renderWidgetTypeFields(ctx: WidgetFieldContext, t: TFunction): ReactNode {
  const { widget, update } = ctx;

  switch (widget.type) {
    case "value":
      return (
        <>
          <Section title={t("editor.section.valueDisplay")} />
          <label>
            {t("editor.unit")}
            <input value={widget.unit ?? ""} onChange={(e) => update({ unit: e.target.value })} />
          </label>
          <label>
            {t("editor.unitField")}
            <input
              value={widget.unitField ?? ""}
              onChange={(e) => update({ unitField: e.target.value || undefined })}
            />
          </label>
          <label>
            {t("editor.decimals")}
            <input
              type="number"
              min={0}
              max={6}
              value={widget.decimals ?? 1}
              onChange={(e) => update({ decimals: Number(e.target.value) })}
            />
          </label>
        </>
      );

    case "toggle":
      return (
        <>
          <Section title={t("editor.section.toggle")} />
          <label>
            {t("editor.trueLabelOn")}
            <input
              value={widget.trueLabel ?? ""}
              onChange={(e) => update({ trueLabel: e.target.value || undefined })}
            />
          </label>
          <label>
            {t("editor.trueLabelOff")}
            <input
              value={widget.falseLabel ?? ""}
              onChange={(e) => update({ falseLabel: e.target.value || undefined })}
            />
          </label>
        </>
      );

    case "indicator":
      return (
        <>
          <Section title={t("editor.section.indicator")} />
          <label>
            {t("editor.trueLabel")}
            <input
              value={widget.trueLabel ?? ""}
              onChange={(e) => update({ trueLabel: e.target.value || undefined })}
            />
          </label>
          <label>
            {t("editor.falseLabel")}
            <input
              value={widget.falseLabel ?? ""}
              onChange={(e) => update({ falseLabel: e.target.value || undefined })}
            />
          </label>
          <label>
            {t("editor.trueColor")}
            <input
              type="color"
              value={widget.trueColor ?? "#3fb950"}
              onChange={(e) => update({ trueColor: e.target.value })}
            />
          </label>
          <label>
            {t("editor.falseColor")}
            <input
              type="color"
              value={widget.falseColor ?? "#f85149"}
              onChange={(e) => update({ falseColor: e.target.value })}
            />
          </label>
        </>
      );

    case "chart":
      return (
        <>
          <Section title={t("editor.section.chart")} />
          <label>
            {t("editor.historyRange")}
            <select
              value={widget.historyRange ?? "live"}
              onChange={(e) => update({ historyRange: e.target.value as typeof widget.historyRange })}
            >
              {WIDGET_HISTORY_RANGE_OPTIONS.map((item) => (
                <option key={item.id} value={item.id}>
                  {t(`history.${item.id}`)}
                </option>
              ))}
            </select>
          </label>
          <label>
            {t("editor.chartType")}
            <select
              value={widget.chartType ?? widget.chartStyle ?? "area"}
              onChange={(e) => update({ chartType: e.target.value as typeof widget.chartType })}
            >
              <option value="line">line</option>
              <option value="area">area</option>
              <option value="bar">bar</option>
              <option value="candlestick">candlestick</option>
              <option value="bubble">bubble</option>
              <option value="radar">radar</option>
              <option value="range">range</option>
            </select>
          </label>
          <label>
            {t("editor.chartStyle")}
            <select
              value={widget.chartStyle ?? "area"}
              onChange={(e) => update({ chartStyle: e.target.value as "line" | "area" })}
            >
              <option value="area">area</option>
              <option value="line">line</option>
            </select>
          </label>
          <label>
            {t("editor.maxPoints")}
            <input
              type="number"
              min={10}
              max={500}
              value={widget.maxPoints ?? 120}
              onChange={(e) => update({ maxPoints: Number(e.target.value) })}
            />
          </label>
          <label>
            {t("editor.color")}
            <input
              type="color"
              value={widget.color ?? "#2f81f7"}
              onChange={(e) => update({ color: e.target.value })}
            />
          </label>
          <label>
            {t("editor.unit")}
            <input value={widget.unit ?? ""} onChange={(e) => update({ unit: e.target.value })} />
          </label>
          <label>
            unitField
            <input
              value={widget.unitField ?? ""}
              onChange={(e) => update({ unitField: e.target.value || undefined })}
            />
          </label>
          <label>
            {t("editor.decimals")}
            <input
              type="number"
              min={0}
              max={6}
              value={widget.decimals ?? 1}
              onChange={(e) => update({ decimals: Number(e.target.value) })}
            />
          </label>
        </>
      );

    case "sparkline":
      return (
        <>
          <Section title={t("editor.section.sparkline")} />
          <label>
            historyRange
            <select
              value={widget.historyRange ?? "live"}
              onChange={(e) => update({ historyRange: e.target.value as typeof widget.historyRange })}
            >
              {WIDGET_HISTORY_RANGE_OPTIONS.map((item) => (
                <option key={item.id} value={item.id}>
                  {t(`history.${item.id}`)}
                </option>
              ))}
            </select>
          </label>
          <label>
            maxPoints
            <input
              type="number"
              min={10}
              max={200}
              value={widget.maxPoints ?? 40}
              onChange={(e) => update({ maxPoints: Number(e.target.value) })}
            />
          </label>
          <label>
            {t("editor.color")}
            <input
              type="color"
              value={widget.color ?? "#3fb950"}
              onChange={(e) => update({ color: e.target.value })}
            />
          </label>
          <label>
            {t("editor.decimals")}
            <input
              type="number"
              min={0}
              max={6}
              value={widget.decimals ?? 1}
              onChange={(e) => update({ decimals: Number(e.target.value) })}
            />
          </label>
        </>
      );

    case "function":
      return (
        <>
          <Section title={t("editor.section.functionInvoke")} />
          <label>
            functionName
            <input
              value={widget.functionName}
              onChange={(e) => update({ functionName: e.target.value })}
            />
          </label>
          <label>
            buttonLabel
            <input
              value={widget.buttonLabel ?? ""}
              onChange={(e) => update({ buttonLabel: e.target.value })}
            />
          </label>
          <label>
            confirmMessage
            <input
              value={widget.confirmMessage ?? ""}
              onChange={(e) => update({ confirmMessage: e.target.value || undefined })}
            />
          </label>
          <label>
            {t("editor.inputJsonStatic")}
            <textarea
              rows={3}
              value={widget.inputJson ?? ""}
              onChange={(e) => update({ inputJson: e.target.value || undefined })}
            />
          </label>
        </>
      );

    case "function-form":
      return (
        <>
          <Section title={t("editor.section.functionForm")} />
          <label>
            functionName
            <input
              value={widget.functionName}
              onChange={(e) => update({ functionName: e.target.value })}
            />
          </label>
          <label>
            buttonLabel
            <input
              value={widget.buttonLabel ?? ""}
              onChange={(e) => update({ buttonLabel: e.target.value })}
            />
          </label>
          <label>
            confirmMessage
            <input
              value={widget.confirmMessage ?? ""}
              onChange={(e) => update({ confirmMessage: e.target.value || undefined })}
            />
          </label>
          <label className="full">
            fieldsJson
            <textarea
              rows={5}
              value={widget.fieldsJson ?? "[]"}
              onChange={(e) => update({ fieldsJson: e.target.value })}
            />
          </label>
        </>
      );

    case "progress":
      return (
        <>
          <Section title={t("editor.section.progress")} />
          <label>
            currentVariable
            <input
              value={widget.currentVariable}
              onChange={(e) => update({ currentVariable: e.target.value })}
            />
          </label>
          <label>
            maxVariable
            <input
              value={widget.maxVariable}
              onChange={(e) => update({ maxVariable: e.target.value })}
            />
          </label>
          <label>
            unit
            <input value={widget.unit ?? ""} onChange={(e) => update({ unit: e.target.value })} />
          </label>
          <label>
            decimals
            <input
              type="number"
              min={0}
              max={6}
              value={widget.decimals ?? 1}
              onChange={(e) => update({ decimals: Number(e.target.value) })}
            />
          </label>
        </>
      );

    case "object-table":
      return (
        <>
          <Section title={t("editor.section.objectTable")} />
          <label className="full">
            columnsJson
            <textarea
              rows={4}
              value={widget.columnsJson ?? "[]"}
              onChange={(e) => update({ columnsJson: e.target.value })}
              placeholder='[{"variable":"temperature","label":"T"}]'
            />
          </label>
          {rowNavigationFields(ctx, "row", t)}
        </>
      );

    case "event-feed":
      return (
        <>
          <Section title={t("editor.section.eventFeed")} />
          <label>
            objectPathPrefix
            <input
              value={widget.objectPathPrefix ?? ""}
              onChange={(e) => update({ objectPathPrefix: e.target.value || undefined })}
              placeholder="root.platform.devices"
            />
          </label>
          <label className="full">
            eventNamesJson
            <textarea
              rows={3}
              value={widget.eventNamesJson ?? "[]"}
              onChange={(e) => update({ eventNamesJson: e.target.value })}
            />
          </label>
          <label>
            maxItems
            <input
              type="number"
              min={5}
              max={100}
              value={widget.maxItems ?? 20}
              onChange={(e) => update({ maxItems: Number(e.target.value) })}
            />
          </label>
          <label>
            payloadFilterExpr
            <input
              value={widget.payloadFilterExpr ?? ""}
              onChange={(e) => update({ payloadFilterExpr: e.target.value || undefined })}
              placeholder="count>10 && name contains abc"
            />
          </label>
        </>
      );

    case "work-queue":
      return (
        <>
          <Section title={t("editor.section.workQueue")} />
          <label>
            operatorId
            <input
              value={widget.operatorId ?? "operator"}
              onChange={(e) => update({ operatorId: e.target.value })}
            />
          </label>
          <label>
            maxItems
            <input
              type="number"
              min={5}
              max={100}
              value={widget.maxItems ?? 20}
              onChange={(e) => update({ maxItems: Number(e.target.value) })}
            />
          </label>
        </>
      );

    case "gauge":
      return (
        <>
          <Section title={t("editor.section.gauge")} />
          <label>
            minVariable
            <input
              value={widget.minVariable ?? ""}
              onChange={(e) => update({ minVariable: e.target.value || undefined })}
            />
          </label>
          <label>
            maxVariable
            <input
              value={widget.maxVariable ?? ""}
              onChange={(e) => update({ maxVariable: e.target.value || undefined })}
            />
          </label>
          <label>
            {t("editor.minValueNoVariable")}
            <input
              type="number"
              value={widget.minValue ?? 0}
              onChange={(e) => update({ minValue: Number(e.target.value) })}
            />
          </label>
          <label>
            {t("editor.maxValueNoVariable")}
            <input
              type="number"
              value={widget.maxValue ?? 100}
              onChange={(e) => update({ maxValue: Number(e.target.value) })}
            />
          </label>
          <label>
            unit
            <input value={widget.unit ?? ""} onChange={(e) => update({ unit: e.target.value })} />
          </label>
          <label>
            decimals
            <input
              type="number"
              min={0}
              max={6}
              value={widget.decimals ?? 1}
              onChange={(e) => update({ decimals: Number(e.target.value) })}
            />
          </label>
        </>
      );

    case "linear-gauge":
      return (
        <>
          <Section title={t("editor.section.linearGauge")} />
          <label>
            minVariable
            <input
              value={widget.minVariable ?? ""}
              onChange={(e) => update({ minVariable: e.target.value || undefined })}
            />
          </label>
          <label>
            maxVariable
            <input
              value={widget.maxVariable ?? ""}
              onChange={(e) => update({ maxVariable: e.target.value || undefined })}
            />
          </label>
          <label>
            minValue
            <input
              type="number"
              value={widget.minValue ?? 0}
              onChange={(e) => update({ minValue: Number(e.target.value) })}
            />
          </label>
          <label>
            maxValue
            <input
              type="number"
              value={widget.maxValue ?? 100}
              onChange={(e) => update({ maxValue: Number(e.target.value) })}
            />
          </label>
          <label>
            unit
            <input value={widget.unit ?? ""} onChange={(e) => update({ unit: e.target.value })} />
          </label>
          <label>
            decimals
            <input
              type="number"
              min={0}
              max={6}
              value={widget.decimals ?? 1}
              onChange={(e) => update({ decimals: Number(e.target.value) })}
            />
          </label>
        </>
      );

    case "liquid-gauge":
      return (
        <>
          <Section title={t("editor.section.liquidGauge")} />
          <label>
            minVariable
            <input
              value={widget.minVariable ?? ""}
              onChange={(e) => update({ minVariable: e.target.value || undefined })}
            />
          </label>
          <label>
            maxVariable
            <input
              value={widget.maxVariable ?? ""}
              onChange={(e) => update({ maxVariable: e.target.value || undefined })}
            />
          </label>
          <label>
            minValue
            <input
              type="number"
              value={widget.minValue ?? 0}
              onChange={(e) => update({ minValue: Number(e.target.value) })}
            />
          </label>
          <label>
            maxValue
            <input
              type="number"
              value={widget.maxValue ?? 100}
              onChange={(e) => update({ maxValue: Number(e.target.value) })}
            />
          </label>
          <label>
            decimals
            <input
              type="number"
              min={0}
              max={6}
              value={widget.decimals ?? 1}
              onChange={(e) => update({ decimals: Number(e.target.value) })}
            />
          </label>
        </>
      );

    case "card-grid":
      return (
        <>
          <Section title={t("editor.section.cardGrid")} />
          <label className="full">
            variablesJson
            <textarea
              rows={3}
              value={widget.variablesJson ?? "[]"}
              onChange={(e) => update({ variablesJson: e.target.value })}
            />
          </label>
          {rowNavigationFields(ctx, "card", t)}
        </>
      );

    case "dashboard-link":
      return (
        <>
          <Section title={t("editor.section.dashboardLink")} />
          <DashboardPathField
            caption="targetDashboardPath"
            value={widget.targetDashboardPath}
            dashboards={ctx.dashboards}
            onChange={(v) => update({ targetDashboardPath: v })}
          />
          <label>
            openMode
            <StackedSlot>
              <select
                value={widget.openMode ?? "navigate"}
                onChange={(e) => update({ openMode: e.target.value as "navigate" | "modal" })}
              >
                <option value="navigate">navigate</option>
                <option value="modal">modal</option>
              </select>
            </StackedSlot>
          </label>
          <label>
            buttonLabel
            <input
              value={widget.buttonLabel ?? ""}
              onChange={(e) => update({ buttonLabel: e.target.value })}
            />
          </label>
          <label>
            modalTitle
            <input
              value={widget.modalTitle ?? ""}
              onChange={(e) => update({ modalTitle: e.target.value || undefined })}
              disabled={widget.openMode !== "modal"}
            />
          </label>
          <label>
            confirmMessage
            <input
              value={widget.confirmMessage ?? ""}
              onChange={(e) => update({ confirmMessage: e.target.value || undefined })}
            />
          </label>
          <label className="full">
            contextSelectionJson
            <textarea
              rows={2}
              value={widget.contextSelectionJson ?? ""}
              onChange={(e) => update({ contextSelectionJson: e.target.value || undefined })}
            />
          </label>
          <label className="full">
            contextParamsJson
            <textarea
              rows={2}
              value={widget.contextParamsJson ?? ""}
              onChange={(e) => update({ contextParamsJson: e.target.value || undefined })}
            />
          </label>
        </>
      );

    case "report": {
      const rw = widget;
      return (
        <>
          <Section
            title={t("editor.section.report")}
            hint={t("editor.reportHint")}
          />
          <PathSelect
            label="reportPath"
            value={rw.reportPath}
            objects={ctx.reports.map((r) => ({ ...r, variableNames: [] }))}
            onChange={(path) => update({ reportPath: path })}
            placeholder="root.platform.reports.ready-items"
          />
          <ReportParameterHints reportPath={rw.reportPath} />
          <label className="full">
            {t("editor.parametersJsonStatic")}
            <textarea
              rows={2}
              className="mono"
              value={rw.parametersJson ?? ""}
              onChange={(e) => update({ parametersJson: e.target.value || undefined })}
              placeholder='{"status":"ready"}'
            />
          </label>
          <label className="full">
            {t("editor.contextParamsJsonReport")}
            <textarea
              rows={2}
              className="mono"
              value={rw.contextParamsJson ?? ""}
              onChange={(e) => update({ contextParamsJson: e.target.value || undefined })}
              placeholder='{"status":"filterStatus"}'
            />
          </label>
          <label>
            emptyMessage
            <input
              value={rw.emptyMessage ?? ""}
              onChange={(e) => update({ emptyMessage: e.target.value || undefined })}
            />
          </label>
          <FormRow>
            <FieldLabel caption="showCsv">
              <select
                value={rw.showCsv === false ? "false" : "true"}
                onChange={(e) => update({ showCsv: e.target.value === "true" })}
              >
                <option value="true">{t("common:action.yes")}</option>
                <option value="false">{t("common:action.no")}</option>
              </select>
            </FieldLabel>
            <FieldLabel caption="showTruncatedWarning">
              <select
                value={rw.showTruncatedWarning === false ? "false" : "true"}
                onChange={(e) => update({ showTruncatedWarning: e.target.value === "true" })}
              >
                <option value="true">{t("common:action.yes")}</option>
                <option value="false">{t("common:action.no")}</option>
              </select>
            </FieldLabel>
          </FormRow>
          <FormRow>
            <FieldLabel caption="showPdf">
              <select
                value={rw.showPdf === false ? "false" : "true"}
                onChange={(e) => update({ showPdf: e.target.value === "true" })}
              >
                <option value="true">{t("editor.yesWithYarg")}</option>
                <option value="false">{t("common:action.no")}</option>
              </select>
            </FieldLabel>
            <FieldLabel caption="showXlsx">
              <select
                value={rw.showXlsx === false ? "false" : "true"}
                onChange={(e) => update({ showXlsx: e.target.value === "true" })}
              >
                <option value="true">{t("editor.yesWithYarg")}</option>
                <option value="false">{t("common:action.no")}</option>
              </select>
            </FieldLabel>
          </FormRow>
          <label>
            showHtml
            <select
              value={rw.showHtml === false ? "false" : "true"}
              onChange={(e) => update({ showHtml: e.target.value === "true" })}
            >
              <option value="true">{t("editor.yesWithYarg")}</option>
              <option value="false">{t("common:action.no")}</option>
            </select>
          </label>
        </>
      );
    }

    case "pie-chart":
      return (
        <>
          <Section title={t("editor.section.pieChart")} />
          <label>
            labelField
            <input
              value={widget.labelField ?? "name"}
              onChange={(e) => update({ labelField: e.target.value })}
            />
          </label>
          <label>
            decimals
            <input
              type="number"
              min={0}
              max={6}
              value={widget.decimals ?? 1}
              onChange={(e) => update({ decimals: Number(e.target.value) })}
            />
          </label>
        </>
      );

    case "history-table":
      return (
        <>
          <Section title={t("editor.section.historyTable")} />
          <label>
            decimals
            <input
              type="number"
              min={0}
              max={6}
              value={widget.decimals ?? 2}
              onChange={(e) => update({ decimals: Number(e.target.value) })}
            />
          </label>
        </>
      );

    case "variable-editor":
      return (
        <>
          <Section title={t("editor.section.variableEditor")} />
          <label className="full">
            {t("editor.variablesJsonAll")}
            <textarea
              rows={3}
              value={widget.variablesJson ?? "[]"}
              onChange={(e) => update({ variablesJson: e.target.value })}
            />
          </label>
        </>
      );

    case "spreadsheet":
      return (
        <>
          <Section title={t("editor.section.spreadsheet")} />
          <label>
            {t("editor.variableNameRequired")}
            <input
              value={widget.variableName}
              onChange={(e) => update({ variableName: e.target.value })}
            />
          </label>
          <label>
            <input
              type="checkbox"
              checked={widget.editable === true}
              onChange={(e) => update({ editable: e.target.checked })}
            />
            {t("editor.editableAllowWrite")}
          </label>
        </>
      );

    case "svg-widget":
      return (
        <>
          <Section title="SVG" />
          <label>
            svgUrl
            <input
              value={widget.svgUrl}
              onChange={(e) => update({ svgUrl: e.target.value })}
              placeholder="/lab-assets/button.svg"
            />
          </label>
          <label>
            clickAction
            <select
              value={widget.clickAction ?? ""}
              onChange={(e) =>
                update({
                  clickAction: (e.target.value || undefined) as "function" | "toggle" | undefined,
                })
              }
            >
              <option value="">—</option>
              <option value="function">function</option>
              <option value="toggle">toggle</option>
            </select>
          </label>
          {widget.clickAction === "function" && (
            <label>
              functionName
              <input
                value={widget.functionName ?? ""}
                onChange={(e) => update({ functionName: e.target.value })}
              />
            </label>
          )}
          {widget.clickAction === "toggle" && (
            <label>
              toggleVariable
              <input
                value={widget.toggleVariable ?? widget.variableName ?? ""}
                onChange={(e) => update({ toggleVariable: e.target.value })}
              />
            </label>
          )}
          <label>
            confirmMessage
            <input
              value={widget.confirmMessage ?? ""}
              onChange={(e) => update({ confirmMessage: e.target.value || undefined })}
            />
          </label>
        </>
      );

    case "sub-dashboard":
      return (
        <>
          <Section title={t("editor.section.subDashboard")} />
          <DashboardPathField
            caption="targetDashboardPath"
            value={widget.targetDashboardPath ?? ""}
            dashboards={ctx.dashboards}
            onChange={(v) => update({ targetDashboardPath: v || undefined })}
          />
          <label>
            {t("editor.targetDashboardPathKeyFromParams")}
            <StackedSlot>
              <input
                value={widget.targetDashboardPathKey ?? ""}
                onChange={(e) => update({ targetDashboardPathKey: e.target.value || undefined })}
              />
            </StackedSlot>
          </label>
          <label>
            <input
              type="checkbox"
              checked={widget.inheritContext !== false}
              onChange={(e) => update({ inheritContext: e.target.checked })}
            />
            inheritContext
          </label>
        </>
      );

    case "panel":
      return (
        <>
          <Section title={t("editor.section.panel")} />
          <label>
            variant
            <input
              value={widget.variant ?? "simple"}
              onChange={(e) => update({ variant: e.target.value as "simple" })}
            />
          </label>
          <label>
            <input
              type="checkbox"
              checked={widget.collapsible === true}
              onChange={(e) => update({ collapsible: e.target.checked })}
            />
            collapsible
          </label>
          <label className="full">
            childrenJson
            <textarea
              rows={8}
              value={widget.childrenJson ?? "[]"}
              onChange={(e) => update({ childrenJson: e.target.value })}
            />
          </label>
        </>
      );

    case "composite-widget":
    case "drawer-panel":
      return (
        <>
          <Section title={widget.type === "drawer-panel" ? t("editor.drawerPanel") : t("editor.composite")} />
          {widget.type === "drawer-panel" && (
            <label>
              drawerLabel
              <input
                value={widget.drawerLabel ?? ""}
                onChange={(e) => update({ drawerLabel: e.target.value || undefined })}
              />
            </label>
          )}
          <label className="full">
            childrenJson
            <textarea
              rows={8}
              value={widget.childrenJson ?? "[]"}
              onChange={(e) => update({ childrenJson: e.target.value })}
            />
          </label>
        </>
      );

    case "tab-panel":
      return (
        <>
          <Section title={t("editor.section.tabs")} />
          <label className="full">
            tabsJson
            <textarea
              rows={8}
              value={widget.tabsJson ?? "[]"}
              onChange={(e) => update({ tabsJson: e.target.value })}
            />
          </label>
        </>
      );

    case "map":
      return (
        <>
          <Section title={t("editor.section.map")} />
          <label>
            latVariable
            <input
              value={widget.latVariable ?? "coordinates"}
              onChange={(e) => update({ latVariable: e.target.value })}
            />
          </label>
          <label>
            latField
            <input
              value={widget.latField ?? "latitude"}
              onChange={(e) => update({ latField: e.target.value })}
            />
          </label>
          <label>
            lonField
            <input
              value={widget.lonField ?? "longitude"}
              onChange={(e) => update({ lonField: e.target.value })}
            />
          </label>
          <label>
            labelVariable
            <input
              value={widget.labelVariable ?? ""}
              onChange={(e) => update({ labelVariable: e.target.value || undefined })}
            />
          </label>
          <label>
            zoom
            <input
              type="number"
              min={1}
              max={18}
              value={widget.zoom ?? 10}
              onChange={(e) => update({ zoom: Number(e.target.value) })}
            />
          </label>
          <label>
            centerLat
            <input
              type="number"
              step="any"
              value={widget.centerLat ?? 55.75}
              onChange={(e) => update({ centerLat: Number(e.target.value) })}
            />
          </label>
          <label>
            centerLon
            <input
              type="number"
              step="any"
              value={widget.centerLon ?? 37.62}
              onChange={(e) => update({ centerLon: Number(e.target.value) })}
            />
          </label>
          <label>
            mapStyleUrl
            <input
              value={widget.mapStyleUrl ?? ""}
              onChange={(e) => update({ mapStyleUrl: e.target.value || undefined })}
              placeholder={t("editor.placeholder.vectorStyle")}
            />
          </label>
          <label>
            tileUrl
            <input
              value={widget.tileUrl ?? ""}
              onChange={(e) => update({ tileUrl: e.target.value || undefined })}
              placeholder="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
          </label>
          <label>
            tileAttribution
            <input
              value={widget.tileAttribution ?? ""}
              onChange={(e) => update({ tileAttribution: e.target.value || undefined })}
            />
          </label>
          {rowNavigationFields(ctx, "row", t)}
        </>
      );

    case "label":
      return (
        <>
          <Section title={t("editor.section.label")} />
          <label>
            {t("editor.textStatic")}
            <input
              value={widget.text ?? ""}
              onChange={(e) => update({ text: e.target.value || undefined })}
            />
          </label>
          <label>
            {t("editor.textJsonAlt")}
            <input
              value={widget.textJson ?? ""}
              onChange={(e) => update({ textJson: e.target.value || undefined })}
            />
          </label>
        </>
      );

    case "image":
      return (
        <>
          <Section title={t("editor.section.image")} />
          <label>
            imageUrl
            <input
              value={widget.imageUrl ?? ""}
              onChange={(e) => update({ imageUrl: e.target.value || undefined })}
            />
          </label>
          <label>
            alt
            <input
              value={widget.alt ?? ""}
              onChange={(e) => update({ alt: e.target.value || undefined })}
            />
          </label>
        </>
      );

    case "html-snippet":
      return (
        <>
          <Section title="HTML" />
          <label className="full">
            htmlJson
            <textarea
              rows={6}
              value={widget.htmlJson ?? ""}
              onChange={(e) => update({ htmlJson: e.target.value || undefined })}
            />
          </label>
        </>
      );

    case "object-tree":
      return (
        <>
          <Section title={t("editor.section.objectTree")} />
          <label>
            maxDepth
            <input
              type="number"
              min={1}
              max={10}
              value={widget.maxDepth ?? 3}
              onChange={(e) => update({ maxDepth: Number(e.target.value) })}
            />
          </label>
        </>
      );

    case "breadcrumbs":
      return (
        <>
          <Section title={t("editor.section.breadcrumbs")} />
          <label>
            {t("editor.pathKeyInParams")}
            <input
              value={widget.pathKey ?? ""}
              onChange={(e) => update({ pathKey: e.target.value || undefined })}
            />
          </label>
          <label>
            separator
            <input
              value={widget.separator ?? " / "}
              onChange={(e) => update({ separator: e.target.value })}
            />
          </label>
        </>
      );

    case "timer":
      return (
        <>
          <Section title={t("editor.section.timer")} />
          <label>
            mode
            <select
              value={widget.mode ?? "countdown"}
              onChange={(e) => update({ mode: e.target.value as "countdown" | "elapsed" })}
            >
              <option value="countdown">countdown</option>
              <option value="elapsed">elapsed</option>
            </select>
          </label>
          <label>
            durationSeconds (countdown)
            <input
              type="number"
              min={1}
              value={widget.durationSeconds ?? 60}
              onChange={(e) => update({ durationSeconds: Number(e.target.value) })}
            />
          </label>
        </>
      );

    case "context-list":
      return (
        <Section
          title={t("editor.section.sessionContext")}
          hint={t("editor.section.sessionContextHint")}
        />
      );

    case "input-form":
      return (
        <>
          <Section title={t("editor.section.inputForm")} />
          <label>
            buttonLabel
            <input
              value={widget.buttonLabel ?? ""}
              onChange={(e) => update({ buttonLabel: e.target.value || undefined })}
            />
          </label>
          <label className="full">
            fieldsJson
            <textarea
              rows={6}
              value={widget.fieldsJson ?? "[]"}
              onChange={(e) => update({ fieldsJson: e.target.value })}
            />
          </label>
        </>
      );

    case "carousel":
      return (
        <>
          <Section title={t("editor.section.carousel")} />
          <label className="full">
            slidesJson
            <textarea
              rows={6}
              value={widget.slidesJson ?? "[]"}
              onChange={(e) => update({ slidesJson: e.target.value })}
            />
          </label>
          <label>
            {t("editor.autoplayOff")}
            <input
              type="number"
              min={0}
              value={widget.autoplayMs ?? 0}
              onChange={(e) => update({ autoplayMs: Number(e.target.value) })}
            />
          </label>
        </>
      );

    case "steps-panel":
      return (
        <>
          <Section title={t("editor.section.steps")} />
          <label className="full">
            stepsJson
            <textarea
              rows={6}
              value={widget.stepsJson ?? "[]"}
              onChange={(e) => update({ stepsJson: e.target.value })}
            />
          </label>
          <label>
            {t("editor.activeStepKeyInParams")}
            <input
              value={widget.activeStepKey ?? ""}
              onChange={(e) => update({ activeStepKey: e.target.value || undefined })}
            />
          </label>
        </>
      );

    case "gantt-chart":
      return (
        <>
          <Section title={t("editor.section.gantt")} />
          <label>
            labelField
            <input
              value={widget.labelField ?? "label"}
              onChange={(e) => update({ labelField: e.target.value })}
            />
          </label>
          <label>
            startField
            <input
              value={widget.startField ?? "start"}
              onChange={(e) => update({ startField: e.target.value })}
            />
          </label>
          <label>
            endField
            <input
              value={widget.endField ?? "end"}
              onChange={(e) => update({ endField: e.target.value })}
            />
          </label>
        </>
      );

    case "network-graph":
      return (
        <>
          <Section title={t("editor.section.networkGraph")} />
          <label>
            nodesVariable
            <input
              value={widget.nodesVariable ?? ""}
              onChange={(e) => update({ nodesVariable: e.target.value || undefined })}
            />
          </label>
          <label>
            edgesVariable
            <input
              value={widget.edgesVariable ?? ""}
              onChange={(e) => update({ edgesVariable: e.target.value || undefined })}
            />
          </label>
          <label>
            labelField
            <input
              value={widget.labelField ?? "label"}
              onChange={(e) => update({ labelField: e.target.value })}
            />
          </label>
        </>
      );

    case "nav-menu":
      return (
        <>
          <Section title={t("editor.section.navMenu")} />
          <label className="full">
            itemsJson
            <textarea
              rows={6}
              value={widget.itemsJson ?? "[]"}
              onChange={(e) => update({ itemsJson: e.target.value })}
              placeholder={t("editor.navMenuPlaceholder")}
            />
          </label>
        </>
      );

    case "status-badge":
      return (
        <Section title={t("editor.section.status")} hint={t("editor.statusHint")} />
      );

    default:
      return null;
  }
}

export function WidgetTypeSpecificFields(ctx: WidgetFieldContext) {
  const { t } = useTranslation(["widgets", "common"]);
  return <FieldPairs>{renderWidgetTypeFields(ctx, t)}</FieldPairs>;
}
