import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import Map, { Marker, Popup, type MapRef } from "react-map-gl/maplibre";
import { useQuery } from "@tanstack/react-query";
import { fetchObjects, fetchVariables } from "../../../api";
import type { MapWidget } from "../../../types/dashboard";
import { readFieldValue } from "../../../types/dashboard";
import { parseJsonObject } from "../dashboardUtils";
import { resolveMapStyle } from "../mapStyleUtils";
import { triggerDashboardOpen, useDashboardContext } from "../DashboardContext";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";
import "maplibre-gl/dist/maplibre-gl.css";

interface MapWidgetViewProps {
  widget: MapWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

interface MarkerPopup {
  path: string;
  lat: number;
  lon: number;
  label: string;
}

export default function MapWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: MapWidgetViewProps) {
  const { t } = useTranslation("widgets");
  const styles = useWidgetStyles(widget.stylesJson);
  const { setSelection, navigateToDashboard, openDashboardModal } = useDashboardContext();
  const [gpsByPath, setGpsByPath] = useState<Record<string, boolean>>({});
  const [popup, setPopup] = useState<MarkerPopup | null>(null);
  const mapRef = useRef<MapRef>(null);

  const children = useQuery({
    queryKey: ["objects", widget.parentPath],
    queryFn: () => fetchObjects(widget.parentPath),
    enabled: Boolean(widget.parentPath),
    refetchInterval: refreshIntervalMs,
  });

  const markerObjects = useMemo(
    () =>
      (children.data ?? []).map((obj) => ({
        path: obj.path,
        displayName: obj.displayName,
      })),
    [children.data]
  );

  const markerPathsKey = markerObjects.map((obj) => obj.path).join("|");

  useEffect(() => {
    setGpsByPath({});
  }, [markerPathsKey]);

  const reportGps = useCallback((path: string, hasGps: boolean) => {
    setGpsByPath((prev) => (prev[path] === hasGps ? prev : { ...prev, [path]: hasGps }));
  }, []);

  const reportedCount = markerObjects.filter((obj) => gpsByPath[obj.path] !== undefined).length;
  const missingCount = markerObjects.filter((obj) => gpsByPath[obj.path] === false).length;

  const centerLat = widget.centerLat ?? 55.75;
  const centerLon = widget.centerLon ?? 37.62;
  const zoom = widget.zoom ?? 10;

  const mapStyle = useMemo(
    () =>
      resolveMapStyle({
        mapStyleUrl: widget.mapStyleUrl,
        tileUrl: widget.tileUrl,
        tileAttribution: widget.tileAttribution,
      }),
    [widget.mapStyleUrl, widget.tileUrl, widget.tileAttribution]
  );

  useEffect(() => {
    mapRef.current?.flyTo({ center: [centerLon, centerLat], zoom, duration: 0 });
  }, [centerLat, centerLon, zoom]);

  const handleMarkerSelect = (path: string, lat: number, lon: number, label: string) => {
    if (editable) return;
    setPopup({ path, lat, lon, label });
    const targetKey = widget.rowSelectionKey ?? widget.selectionKey;
    const openOptions = {
      selection: targetKey ? { [targetKey]: path } : undefined,
      params: parseJsonObject(widget.rowParamsJson),
    };
    if (widget.selectionKey) {
      setSelection(widget.selectionKey, path);
    }
    triggerDashboardOpen(
      widget.rowOpenMode,
      widget.rowTargetDashboard,
      widget.title,
      { navigateToDashboard, openDashboardModal },
      openOptions
    );
  };

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-map"
      editable={editable}
    >
      {!widget.parentPath ? (
        <p className="hint">{t("view.mapParentPathHint")}</p>
      ) : markerObjects.length === 0 ? (
        <p className="hint">{t("view.noChildObjects", { path: widget.parentPath })}</p>
      ) : (
        <div className="dash-map-wrap dash-map-container" style={styles.body}>
          <Map
            ref={mapRef}
            mapStyle={mapStyle}
            initialViewState={{
              longitude: centerLon,
              latitude: centerLat,
              zoom,
            }}
            style={{ width: "100%", height: "100%" }}
            scrollZoom={!editable}
            dragPan={!editable}
            dragRotate={false}
            doubleClickZoom={!editable}
            touchZoomRotate={!editable}
            keyboard={!editable}
            attributionControl={{ compact: true }}
            onClick={() => setPopup(null)}
          >
            {markerObjects.map((obj) => (
              <MapMarker
                key={obj.path}
                obj={obj}
                widget={widget}
                refreshIntervalMs={refreshIntervalMs}
                editable={editable}
                onGpsStatus={reportGps}
                onSelect={(lat, lon, label) => handleMarkerSelect(obj.path, lat, lon, label)}
              />
            ))}
            {popup && (
              <Popup
                longitude={popup.lon}
                latitude={popup.lat}
                anchor="bottom"
                onClose={() => setPopup(null)}
                closeOnClick={false}
              >
                {popup.label}
              </Popup>
            )}
          </Map>
          {reportedCount === markerObjects.length && missingCount > 0 && (
            <p className="hint dash-map-no-gps">
              {t("view.mapMissingCoords", { missing: missingCount, total: markerObjects.length })}
            </p>
          )}
        </div>
      )}
    </DashWidgetShell>
  );
}

function MapMarker({
  obj,
  widget,
  refreshIntervalMs,
  editable,
  onSelect,
  onGpsStatus,
}: {
  obj: { path: string; displayName: string };
  widget: MapWidget;
  refreshIntervalMs: number;
  editable?: boolean;
  onSelect: (lat: number, lon: number, label: string) => void;
  onGpsStatus: (path: string, hasGps: boolean) => void;
}) {
  const vars = useQuery({
    queryKey: ["variables", obj.path],
    queryFn: () => fetchVariables(obj.path),
    refetchInterval: refreshIntervalMs,
  });

  const latVar = widget.latVariable ?? "coordinates";
  const latField = widget.latField ?? "latitude";
  const lonField = widget.lonField ?? "longitude";
  const coordVar = vars.data?.find((v) => v.name === latVar);
  const lat = Number(readFieldValue(coordVar?.value?.rows[0], latField));
  const lon = Number(readFieldValue(coordVar?.value?.rows[0], lonField));
  const hasGps = Number.isFinite(lat) && Number.isFinite(lon);

  useEffect(() => {
    if (!vars.isLoading) {
      onGpsStatus(obj.path, hasGps);
    }
  }, [vars.isLoading, hasGps, obj.path, onGpsStatus]);

  if (!hasGps) {
    return null;
  }

  const labelVar = widget.labelVariable
    ? vars.data?.find((v) => v.name === widget.labelVariable)
    : undefined;
  const label =
    String(readFieldValue(labelVar?.value?.rows[0], "value") ?? "") || obj.displayName;

  return (
    <Marker
      longitude={lon}
      latitude={lat}
      anchor="center"
      onClick={(event) => {
        event.originalEvent.stopPropagation();
        onSelect(lat, lon, label);
      }}
    >
      <button
        type="button"
        className="dash-map-marker"
        title={label}
        disabled={editable}
        aria-label={label}
      />
    </Marker>
  );
}
