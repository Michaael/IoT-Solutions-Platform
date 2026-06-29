import type { MimicConnection, MimicElement, MimicPort } from "../types/scadaMimic";
import { getSymbol } from "./symbols/registry";

export interface PortPosition {
  elementId: string;
  portId: string;
  x: number;
  y: number;
}

export function getElementPortPosition(
  element: MimicElement,
  portId: string
): PortPosition | null {
  const symbol = getSymbol(element.symbolId);
  if (!symbol) return null;
  const port = symbol.ports.find((p) => p.id === portId);
  if (!port) return null;
  const scale = element.scale ?? 1;
  const w = symbol.defaultWidth * scale;
  const h = symbol.defaultHeight * scale;
  const cx = element.x + w / 2;
  const cy = element.y + h / 2;
  const px = element.x + port.x * scale;
  const py = element.y + port.y * scale;
  if (element.rotation === 90) {
    return { elementId: element.id, portId, x: cx + (py - cy), y: cy - (px - cx) };
  }
  if (element.rotation === 180) {
    return { elementId: element.id, portId, x: element.x + w - (px - element.x), y: element.y + h - (py - element.y) };
  }
  if (element.rotation === 270) {
    return { elementId: element.id, portId, x: cx - (py - cy), y: cy + (px - cx) };
  }
  return { elementId: element.id, portId, x: px, y: py };
}

export function connectionPolylinePoints(
  connection: MimicConnection,
  elements: MimicElement[]
): { x: number; y: number }[] {
  if (connection.points.length >= 2) {
    return connection.points;
  }
  const byId = new Map(elements.map((el) => [el.id, el]));
  const fromEl = byId.get(connection.from.elementId);
  const toEl = byId.get(connection.to.elementId);
  if (!fromEl || !toEl) return connection.points;
  const from = getElementPortPosition(fromEl, connection.from.port);
  const to = getElementPortPosition(toEl, connection.to.port);
  if (!from || !to) return connection.points;
  return routeOrthogonal(from.x, from.y, to.x, to.y);
}

export function routeOrthogonal(
  x1: number,
  y1: number,
  x2: number,
  y2: number
): { x: number; y: number }[] {
  const midX = (x1 + x2) / 2;
  return [
    { x: x1, y: y1 },
    { x: midX, y: y1 },
    { x: midX, y: y2 },
    { x: x2, y: y2 },
  ];
}

export function snapPortNear(
  x: number,
  y: number,
  elements: MimicElement[],
  threshold = 24
): PortPosition | null {
  let best: PortPosition | null = null;
  let bestDist = threshold;
  for (const el of elements) {
    const symbol = getSymbol(el.symbolId);
    if (!symbol) continue;
    for (const port of symbol.ports) {
      const pos = getElementPortPosition(el, port.id);
      if (!pos) continue;
      const dist = Math.hypot(pos.x - x, pos.y - y);
      if (dist < bestDist) {
        bestDist = dist;
        best = pos;
      }
    }
  }
  return best;
}

export function findNearestPort(
  elements: MimicElement[],
  x: number,
  y: number,
  excludeElementId?: string
): { element: MimicElement; port: MimicPort; x: number; y: number } | null {
  const hit = snapPortNear(x, y, elements.filter((el) => el.id !== excludeElementId));
  if (!hit) return null;
  const element = elements.find((el) => el.id === hit.elementId);
  if (!element) return null;
  const symbol = getSymbol(element.symbolId);
  const port = symbol?.ports.find((p) => p.id === hit.portId);
  if (!port) return null;
  return { element, port, x: hit.x, y: hit.y };
}

export function rerouteConnectionsForElement(
  elementId: string,
  connections: MimicConnection[],
  elements: MimicElement[]
): MimicConnection[] {
  return connections.map((conn) => {
    if (conn.from.elementId !== elementId && conn.to.elementId !== elementId) {
      return conn;
    }
    const points = connectionPolylinePoints(conn, elements);
    return { ...conn, points };
  });
}
