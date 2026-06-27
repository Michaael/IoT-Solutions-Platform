export interface AuthSession {
  token: string;
  username: string;
  displayName: string;
  roles: string[];
  expiresAt?: string;
  /** When true, web console opens operator app instead of admin shell. */
  autoStartEnabled?: boolean;
  /** Operator manifest app id (e.g. demo, oil-terminal). */
  autoStartApp?: string;
  /** Tenant namespace for scoped operators (root.tenant.{id}.*). */
  tenantId?: string;
}

const SESSION_KEY = "ispf-auth-session";

/** In-memory copy — stays in sync with localStorage (avoids race after clear). */
let cachedSession: AuthSession | null | undefined;

function readSessionFromStorage(): AuthSession | null {
  const raw = localStorage.getItem(SESSION_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as AuthSession;
  } catch {
    return null;
  }
}

export function getStoredSession(): AuthSession | null {
  if (cachedSession !== undefined) {
    return cachedSession;
  }
  cachedSession = readSessionFromStorage();
  return cachedSession;
}

export function setStoredSession(session: AuthSession): void {
  cachedSession = session;
  localStorage.setItem(SESSION_KEY, JSON.stringify(session));
  window.dispatchEvent(new Event("ispf-session-updated"));
}

export function clearStoredSession(): void {
  cachedSession = null;
  localStorage.removeItem(SESSION_KEY);
}

export function getAuthHeaders(): Record<string, string> {
  const session = getStoredSession();
  if (session?.token) {
    return { Authorization: `Bearer ${session.token}` };
  }
  return {};
}

export function getPrimaryRole(session: AuthSession | null): "admin" | "operator" | null {
  if (!session) {
    return null;
  }
  if (session.roles.includes("admin")) {
    return "admin";
  }
  if (session.roles.includes("operator")) {
    return "operator";
  }
  return null;
}

export function isAdminSession(session: AuthSession | null): boolean {
  return session?.roles.includes("admin") ?? false;
}
