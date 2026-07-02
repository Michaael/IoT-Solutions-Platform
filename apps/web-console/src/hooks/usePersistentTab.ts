import { useCallback, useEffect, useState } from "react";

const STORAGE_PREFIX = "ispf:ui:active-tab:";

function readTab<T extends string>(
  storageKey: string,
  fallback: T,
  allowedValues: readonly T[]
): T {
  if (typeof window === "undefined") return fallback;
  try {
    const stored = window.sessionStorage.getItem(`${STORAGE_PREFIX}${storageKey}`);
    return stored && allowedValues.includes(stored as T) ? (stored as T) : fallback;
  } catch {
    return fallback;
  }
}

export function usePersistentTab<T extends string>(
  storageKey: string,
  fallback: T,
  allowedValues: readonly T[]
): [T, (next: T) => void] {
  const allowedSignature = allowedValues.join("\u0000");
  const [tab, setTabState] = useState<T>(() => readTab(storageKey, fallback, allowedValues));

  useEffect(() => {
    setTabState(readTab(storageKey, fallback, allowedValues));
    // The signature tracks value changes without requiring callers to memoize the array.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [storageKey, fallback, allowedSignature]);

  const setTab = useCallback(
    (next: T) => {
      if (!allowedValues.includes(next)) return;
      setTabState(next);
      try {
        window.sessionStorage.setItem(`${STORAGE_PREFIX}${storageKey}`, next);
      } catch {
        // Storage can be unavailable in private or embedded contexts; local state still works.
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [storageKey, allowedSignature]
  );

  return [tab, setTab];
}
