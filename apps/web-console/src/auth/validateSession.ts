import type { AuthMe } from "../api";
import {
  clearStoredSession,
  getStoredSession,
  type AuthSession,
} from "./session";

export const SESSION_INVALID_EVENT = "ispf-session-invalid";
export const SESSION_UPDATED_EVENT = "ispf-session-updated";

export function isSessionExpired(session: AuthSession | null): boolean {
  if (!session?.expiresAt) {
    return false;
  }
  const expiresAt = Date.parse(session.expiresAt);
  return Number.isFinite(expiresAt) && expiresAt <= Date.now();
}

export function invalidateStoredSession(): void {
  clearStoredSession();
  window.dispatchEvent(new Event(SESSION_INVALID_EVENT));
}

/** Returns session when token is present and accepted by /auth/me; clears storage otherwise. */
export async function validateStoredSession(): Promise<AuthSession | null> {
  const session = getStoredSession();
  if (!session?.token?.trim()) {
    clearStoredSession();
    return null;
  }
  if (isSessionExpired(session)) {
    invalidateStoredSession();
    return null;
  }
  try {
    const response = await fetch("/api/v1/auth/me", {
      cache: "no-store",
      headers: { Authorization: `Bearer ${session.token}` },
    });
    if (!response.ok) {
      invalidateStoredSession();
      return null;
    }
    const me = (await response.json()) as AuthMe;
    if (!me.authenticated) {
      invalidateStoredSession();
      return null;
    }
    return session;
  } catch {
    invalidateStoredSession();
    return null;
  }
}
