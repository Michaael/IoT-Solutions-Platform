export function defaultFederationBaseUrl(): string {
  if (import.meta.env.DEV) {
    return "http://127.0.0.1:8080";
  }
  return window.location.origin;
}

export function copyToClipboard(text: string): Promise<void> {
  if (navigator.clipboard?.writeText) {
    return navigator.clipboard.writeText(text);
  }
  const textarea = document.createElement("textarea");
  textarea.value = text;
  textarea.style.position = "fixed";
  textarea.style.opacity = "0";
  document.body.appendChild(textarea);
  textarea.select();
  document.execCommand("copy");
  document.body.removeChild(textarea);
  return Promise.resolve();
}

export type FederationTab = "peers" | "tokens" | "tunnel" | "probe";

export const FEDERATION_TAB_KEYS: Record<FederationTab, string> = {
  peers: "tab.peers",
  tokens: "tab.tokens",
  tunnel: "tab.tunnel",
  probe: "tab.probe",
};
