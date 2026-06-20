import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import type { TreeNode } from "../types";
import {
  ancestorPaths,
  defaultExpandedPaths,
  readExpandedPaths,
  writeExpandedPaths,
} from "../utils/treeExpanded";
import ObjectTreeIcon from "./icons/ObjectTreeIcon";

interface ObjectTreeProps {
  nodes: TreeNode[];
  selectedPath: string | null;
  onSelect: (path: string) => void;
  onOpenEditor?: (path: string) => void;
}

interface TreeExpandedContextValue {
  isExpanded: (path: string) => boolean;
  toggle: (path: string) => void;
}

const TreeExpandedContext = createContext<TreeExpandedContextValue | null>(null);

function useTreeExpanded(): TreeExpandedContextValue {
  const ctx = useContext(TreeExpandedContext);
  if (!ctx) {
    throw new Error("TreeExpandedContext missing");
  }
  return ctx;
}

function TreeItem({
  node,
  selectedPath,
  onSelect,
  onOpenEditor,
  depth,
}: {
  node: TreeNode;
  selectedPath: string | null;
  onSelect: (path: string) => void;
  onOpenEditor?: (path: string) => void;
  depth: number;
}) {
  const { isExpanded, toggle } = useTreeExpanded();
  const path = node.object.path;
  const hasChildren = node.children.length > 0;
  const expanded = isExpanded(path);
  const isSelected = selectedPath === path;

  return (
    <div className="tree-item">
      <button
        type="button"
        className={`tree-row ${isSelected ? "selected" : ""}`}
        style={{ paddingLeft: `${depth * 16 + 8}px` }}
        onClick={() => onSelect(path)}
        onDoubleClick={() => onOpenEditor?.(path)}
      >
        <span
          className="tree-toggle"
          onClick={(e) => {
            if (hasChildren) {
              e.stopPropagation();
              toggle(path);
            }
          }}
        >
          {hasChildren ? (expanded ? "▾" : "▸") : "·"}
        </span>
        <span className="tree-icon">
          <ObjectTreeIcon
            path={node.object.path}
            type={node.object.type}
            iconId={node.object.iconId}
          />
        </span>
        <span className="tree-label">{node.object.displayName}</span>
        <span className="tree-type">{node.object.type}</span>
      </button>
      {expanded &&
        node.children.map((child) => (
          <TreeItem
            key={child.object.path}
            node={child}
            selectedPath={selectedPath}
            onSelect={onSelect}
            onOpenEditor={onOpenEditor}
            depth={depth + 1}
          />
        ))}
    </div>
  );
}

export default function ObjectTree({ nodes, selectedPath, onSelect, onOpenEditor }: ObjectTreeProps) {
  const [expandedPaths, setExpandedPaths] = useState<Set<string>>(() => readExpandedPaths());
  const [defaultsSeeded, setDefaultsSeeded] = useState(() => readExpandedPaths().size > 0);

  const persist = useCallback((next: Set<string>) => {
    setExpandedPaths(next);
    writeExpandedPaths(next);
  }, []);

  useEffect(() => {
    if (defaultsSeeded || nodes.length === 0) {
      return;
    }
    const defaults = defaultExpandedPaths(nodes);
    if (defaults.length === 0) {
      setDefaultsSeeded(true);
      return;
    }
    persist(new Set(defaults));
    setDefaultsSeeded(true);
  }, [nodes, defaultsSeeded, persist]);

  useEffect(() => {
    if (!selectedPath || selectedPath === "root") {
      return;
    }
    const ancestors = ancestorPaths(selectedPath);
    if (ancestors.length === 0) {
      return;
    }
    setExpandedPaths((current) => {
      let changed = false;
      const next = new Set(current);
      for (const path of ancestors) {
        if (!next.has(path)) {
          next.add(path);
          changed = true;
        }
      }
      if (changed) {
        writeExpandedPaths(next);
        return next;
      }
      return current;
    });
  }, [selectedPath]);

  const expandedApi = useMemo<TreeExpandedContextValue>(
    () => ({
      isExpanded: (path: string) => expandedPaths.has(path),
      toggle: (path: string) => {
        setExpandedPaths((current) => {
          const next = new Set(current);
          if (next.has(path)) {
            next.delete(path);
          } else {
            next.add(path);
          }
          writeExpandedPaths(next);
          return next;
        });
      },
    }),
    [expandedPaths]
  );

  return (
    <TreeExpandedContext.Provider value={expandedApi}>
      <div className="object-tree">
        {nodes.map((node) => (
          <TreeItem
            key={node.object.path}
            node={node}
            selectedPath={selectedPath}
            onSelect={onSelect}
            onOpenEditor={onOpenEditor}
            depth={0}
          />
        ))}
      </div>
    </TreeExpandedContext.Provider>
  );
}
