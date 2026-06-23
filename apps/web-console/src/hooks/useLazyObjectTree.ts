import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { fetchObjects } from "../api";
import i18n from "../i18n";
import type { ObjectSummary, TreeNode } from "../types";
import { buildObjectTree, parentObjectPath } from "../utils/tree";
import { objectTreeKey } from "../utils/treeRowKey";
import type { ObjectWsMessage } from "./useObjectWebSocket";

const BOOTSTRAP_PARENTS = ["root", "root.platform"];

export function useLazyObjectTree(enabled = true) {
  const objectsRef = useRef(new Map<string, ObjectSummary>());
  const loadedParentsRef = useRef(new Set<string>());
  const loadingParentsRef = useRef(new Set<string>());
  const [version, setVersion] = useState(0);

  const bump = useCallback(() => setVersion((v) => v + 1), []);

  const removeGroupRefsForParent = useCallback((parentPath: string) => {
    const map = objectsRef.current;
    for (const [key, obj] of map.entries()) {
      if (obj.groupRef && obj.groupContextPath === parentPath) {
        map.delete(key);
      }
    }
  }, []);

  const mergeObjects = useCallback(
    (items: ObjectSummary[], parentPath?: string) => {
      const map = objectsRef.current;
      if (parentPath) {
        removeGroupRefsForParent(parentPath);
      }
      for (const item of items) {
        map.set(objectTreeKey(item), item);
      }
      bump();
    },
    [bump, removeGroupRefsForParent],
  );

  const loadChildren = useCallback(
    async (parentPath: string, force = false) => {
      if (!force && loadedParentsRef.current.has(parentPath)) {
        return;
      }
      if (loadingParentsRef.current.has(parentPath)) {
        return;
      }
      loadingParentsRef.current.add(parentPath);
      try {
        const children = await fetchObjects(parentPath, true);
        mergeObjects(children, parentPath);
        loadedParentsRef.current.add(parentPath);
      } catch (error) {
        loadedParentsRef.current.delete(parentPath);
        console.error(`Failed to load tree children for ${parentPath}`, error);
        throw error;
      } finally {
        loadingParentsRef.current.delete(parentPath);
      }
    },
    [mergeObjects],
  );

  const refreshParent = useCallback(
    async (parentPath: string) => {
      loadedParentsRef.current.delete(parentPath);
      await loadChildren(parentPath, true);
    },
    [loadChildren],
  );

  const [treeLoadError, setTreeLoadError] = useState<string | null>(null);

  useEffect(() => {
    if (!enabled) {
      return;
    }
    void (async () => {
      try {
        setTreeLoadError(null);
        for (const parent of BOOTSTRAP_PARENTS) {
          await loadChildren(parent);
        }
      } catch (error) {
        setTreeLoadError(error instanceof Error ? error.message : i18n.t("common:error.treeLoadFailed"));
      }
    })();
  }, [loadChildren, enabled]);

  useEffect(() => {
    const onStructureChange = (event: Event) => {
      const message = (event as CustomEvent<ObjectWsMessage>).detail;
      if (!message?.path) {
        return;
      }
      void refreshParent(message.path);
      const parent = parentObjectPath(message.path);
      if (parent) {
        void refreshParent(parent);
      }
      if (message.type === "DELETED") {
        const map = objectsRef.current;
        for (const [key, obj] of [...map.entries()]) {
          if (obj.path === message.path) {
            map.delete(key);
          }
        }
        map.delete(message.path);
        bump();
      }
    };
    window.addEventListener("ispf-tree-structure-change", onStructureChange);
    return () => window.removeEventListener("ispf-tree-structure-change", onStructureChange);
  }, [refreshParent, bump]);

  const objects = useMemo(() => [...objectsRef.current.values()], [version]);

  const tree: TreeNode[] = useMemo(() => buildObjectTree(objects), [objects]);

  const invalidateAll = useCallback(async () => {
    loadedParentsRef.current.clear();
    objectsRef.current.clear();
    bump();
    for (const parent of BOOTSTRAP_PARENTS) {
      await loadChildren(parent, true);
    }
  }, [loadChildren, bump]);

  return {
    tree,
    objects,
    loadChildren,
    refreshParent,
    invalidateAll,
    treeLoadError,
    loadingParents: loadingParentsRef.current,
  };
}
