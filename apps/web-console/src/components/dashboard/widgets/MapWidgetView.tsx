import { useEffect, useMemo, useState } from "react";
import { MapContainer, TileLayer, Marker, Popup, useMap } from "react-leaflet";
import L from "leaflet";
import { useQuery } from "@tanstack/react-query";
import { fetchObjects, fetchVariables } from "../../../api";
import type { MapWidget } from "../../../types/dashboard";
import { readFieldValue } from "../../../types/dashboard";
import { parseJsonObject } from "../dashboardUtils";
import { triggerDashboardOpen, useDashboardContext } from "../DashboardContext";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";
import "leaflet/dist/leaflet.css";

// Default marker icons break with bundlers
const defaultIcon = L.icon({
  iconUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png",
  iconRetinaUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png",
  shadowUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png",
  iconSize: [25, 41],
  iconAnchor: [12, 41],
});
L.Marker.prototype.options.icon = defaultIcon;

interface MapWidgetViewProps {
  widget: MapWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

function MapRecenter({ lat, lon, zoom }: { lat: number; lon: number; zoom: number }) {
  const map = useMap();
  useEffect(() => {
    map.setView([lat, lon], zoom);
  }, [map, lat, lon, zoom]);
  return null;
}

export default function MapWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: MapWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);
  const { setSelection, navigateToDashboard, openDashboardModal } = useDashboardContext();
  const [noGps, setNoGps] = useState<string[]>([]);

  const children = useQuery({
    queryKey: ["objects", widget.parentPath],
    queryFn: () => fetchObjects(widget.parentPath),
    enabled: Boolean(widget.parentPath),
    refetchInterval: refreshIntervalMs,
  });

  const markers = useMemo(() => {
    return (children.data ?? []).map((obj) => ({ path: obj.path, displayName: obj.displayName }));
  }, [children.data]);

  const centerLat = widget.centerLat ?? 55.75;
  const centerLon = widget.centerLon ?? 37.62;
  const zoom = widget.zoom ?? 10;

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-map"
      editable={editable}
    >
      {!widget.parentPath ? (
        <p className="hint">Укажите parentPath</p>
      ) : (
        <div className="dash-map-wrap" style={styles.body}>
          <MapContainer
            center={[centerLat, centerLon]}
            zoom={zoom}
            className="dash-map-container"
            scrollWheelZoom={!editable}
          >
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OSM</a>'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <MapRecenter lat={centerLat} lon={centerLon} zoom={zoom} />
            {markers.map((obj) => (
              <MapMarker
                key={obj.path}
                obj={obj}
                widget={widget}
                refreshIntervalMs={refreshIntervalMs}
                editable={editable}
                onMissing={() => setNoGps((list) => [...list, obj.path])}
                onSelect={(path) => {
                  if (editable) return;
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
                }}
              />
            ))}
          </MapContainer>
          {noGps.length > 0 && (
            <p className="hint dash-map-no-gps">Без GPS: {noGps.length}</p>
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
  onMissing,
}: {
  obj: { path: string; displayName: string };
  widget: MapWidget;
  refreshIntervalMs: number;
  editable?: boolean;
  onSelect: (path: string) => void;
  onMissing: () => void;
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

  useEffect(() => {
    if (!vars.isLoading && (!Number.isFinite(lat) || !Number.isFinite(lon))) {
      onMissing();
    }
  }, [vars.isLoading, lat, lon, onMissing]);

  if (!Number.isFinite(lat) || !Number.isFinite(lon)) {
    return null;
  }

  const labelVar = widget.labelVariable
    ? vars.data?.find((v) => v.name === widget.labelVariable)
    : undefined;
  const label =
    String(readFieldValue(labelVar?.value?.rows[0], "value") ?? "") || obj.displayName;

  return (
    <Marker
      position={[lat, lon]}
      eventHandlers={{
        click: () => {
          if (!editable) onSelect(obj.path);
        },
      }}
    >
      <Popup>{label}</Popup>
    </Marker>
  );
}
