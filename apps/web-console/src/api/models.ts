import { getAuthHeaders } from "../auth/session";
import type {
  CreateModelPayload,
  ModelAttachmentDto,
  ModelDto,
  ModelType,
  UpdateModelPayload,
} from "../types/models";
import type { ObjectSummary } from "../types";

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
      ...init?.headers,
    },
    ...init,
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json();
}

export function fetchModels(): Promise<ModelDto[]> {
  return request("/api/v1/models");
}

export function fetchModelByName(name: string): Promise<ModelDto> {
  return request(`/api/v1/models/by-name/${encodeURIComponent(name)}`);
}

export function createModel(payload: CreateModelPayload): Promise<ModelDto> {
  return request("/api/v1/models", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function updateModel(id: string, payload: UpdateModelPayload): Promise<ModelDto> {
  return request(`/api/v1/models/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export function deleteModel(id: string): Promise<void> {
  return request(`/api/v1/models/${encodeURIComponent(id)}`, { method: "DELETE" });
}

export function applyModel(modelId: string, objectPath: string): Promise<ModelAttachmentDto> {
  return request(
    `/api/v1/models/${encodeURIComponent(modelId)}/apply?objectPath=${encodeURIComponent(objectPath)}`,
    { method: "POST" }
  );
}

export function instantiateModel(
  modelId: string,
  parentPath: string,
  instanceName: string,
  parameters?: Record<string, string>
): Promise<ObjectSummary> {
  return request(`/api/v1/models/${encodeURIComponent(modelId)}/instantiate`, {
    method: "POST",
    body: JSON.stringify({ parentPath, instanceName, parameters: parameters ?? {} }),
  });
}

export function createModelFromObject(
  sourcePath: string,
  modelName: string,
  description?: string,
  type?: ModelType
): Promise<ModelDto> {
  return request("/api/v1/models/from-object", {
    method: "POST",
    body: JSON.stringify({ sourcePath, modelName, description, type }),
  });
}

export function fetchModelAttachments(objectPath?: string): Promise<ModelAttachmentDto[]> {
  const query = objectPath ? `?objectPath=${encodeURIComponent(objectPath)}` : "";
  return request(`/api/v1/models/attachments${query}`);
}

export function upgradeModelInstances(modelId: string): Promise<{
  status: string;
  modelVersion: string;
  upgraded: string[];
  count: number;
}> {
  return request(`/api/v1/models/${encodeURIComponent(modelId)}/upgrade-instances`, {
    method: "POST",
  });
}

export function fetchModelInstances(modelId: string): Promise<{ objectPath: string }[]> {
  return request(`/api/v1/models/${encodeURIComponent(modelId)}/instances`);
}

export function upgradeModel(
  modelId: string,
  targetPath: string,
  targetVersion?: string
): Promise<Record<string, unknown>> {
  const params = new URLSearchParams({ targetPath });
  if (targetVersion) params.set("targetVersion", targetVersion);
  return request(`/api/v1/models/${encodeURIComponent(modelId)}/upgrade?${params}`, {
    method: "POST",
  });
}
