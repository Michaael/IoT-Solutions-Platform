import { request } from "./http";

export interface ChangeSetOp {
  op: string;
  path: string;
  expectedRevision?: number | null;
  payload?: Record<string, unknown> | null;
}

export interface ChangeSetSummary {
  id: string;
  title: string;
  author: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface ChangeSetDetail extends ChangeSetSummary {
  baseSnapshot: string | null;
  ops: ChangeSetOp[];
}

export interface ChangeSetPreview {
  changeSetId: string;
  title: string;
  conflicts: Array<Record<string, unknown>>;
  applicable: Array<Record<string, unknown>>;
  conflictCount: number;
}

export interface ChangeSetApplyResult {
  status: string;
  applied: string[];
  count: number;
}

export function fetchChangeSets(status?: string): Promise<ChangeSetSummary[]> {
  const params = status ? `?status=${encodeURIComponent(status)}` : "";
  return request(`/api/v1/platform/change-sets${params}`);
}

export function fetchChangeSet(id: string): Promise<ChangeSetDetail> {
  return request(`/api/v1/platform/change-sets/${encodeURIComponent(id)}`);
}

export function createChangeSet(title: string, ops: ChangeSetOp[]): Promise<ChangeSetDetail> {
  return request("/api/v1/platform/change-sets", {
    method: "POST",
    body: JSON.stringify({ title, ops }),
  });
}

export function previewChangeSet(id: string): Promise<ChangeSetPreview> {
  return request(`/api/v1/platform/change-sets/${encodeURIComponent(id)}/preview`, {
    method: "POST",
  });
}

export function applyChangeSet(id: string, force = false): Promise<ChangeSetApplyResult> {
  const params = force ? "?force=true" : "";
  return request(`/api/v1/platform/change-sets/${encodeURIComponent(id)}/apply${params}`, {
    method: "POST",
  });
}
