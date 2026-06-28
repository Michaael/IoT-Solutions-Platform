import {
  createContext,
  memo,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { useTranslation } from "react-i18next";
import type { ObjectSummary, ObjectType, TreeNode } from "../types";
import { parentObjectPath, siblingObjectPaths } from "../utils/tree";
import {
  ancestorPaths,
  defaultExpandedPaths,
  readExpandedPaths,
  writeExpandedPaths,
} from "../utils/treeExpanded";
import { objectTreeKey, selectionFromObject, type TreeRowSelection } from "../utils/treeRowKey";
import ObjectTreeIcon from "./icons/ObjectTreeIcon";
import TreeBulkContextMenu, { type TreeContextMenuState } from "./TreeBulkContextMenu";
import type { TreeBulkActionsConfig } from "../hooks/useTreeBulkActions";
import { isTreeContainerType } from "../utils/objectTreeTypes";
import { isSpecializedEditorObject } from "../utils/editorObject";
import { deviceDriverTreeClass } from "../utils/deviceDriverTreeTone";

interface ObjectTreeProps {
  nodes: TreeNode[];
  objects: ObjectSummary[];
  selectedPath: string | null;
  selectedKeys: Set<string>;
  onRowSelect: (row: TreeRowSelection, event: { metaKey: boolean; shiftKey: boolean }) => void;
  onOpenEditor?: (path: string) => void;
  canReorder?: boolean;
  onReorder?: (parentPath: string, orderedPaths: string[]) => void;
  onLoadChildren?: (path: string) => void;
  onVisibleRowKeysChange?: (keys: string[]) => void;
  bulkActions?: TreeBulkActionsConfig;
}

interface TreeExpandedContextValue {
  isExpanded: (path: string) => boolean;
  toggle: (path: string) => void;
}

interface TreeDragContextValue {
  canReorder: boolean;
  draggingPath: string | null;
  dropTargetPath: string | null;
  startDrag: (path: string) => void;
  endDrag: () => void;
  setDropTarget: (path: string | null) => void;
  reorder: (parentPath: string, orderedPaths: string[]) => void;
  siblingPaths: (parentPath: string) => string[];
}

interface FlatRow {
  node: TreeNode;
  depth: number;
  rowKey: string;
}

const ROW_HEIGHT = 34;
const OVERSCAN = 8;

const TreeExpandedContext = createContext<TreeExpandedContextValue | null>(null);
const TreeDragContext = createContext<TreeDragContextValue | null>(null);

function useTreeExpanded(): TreeExpandedContextValue {
  const ctx = useContext(TreeExpandedContext);
  if (!ctx) {
    throw new Error("TreeExpandedContext missing");
  }
  return ctx;
}

function useTreeDrag(): TreeDragContextValue | null {
  return useContext(TreeDragContext);
}

export function flattenVisibleNodes(
  nodes: TreeNode[],
  isExpanded: (path: string) => boolean,
  depth = 0,
): FlatRow[] {
  const rows: FlatRow[] = [];
  for (const node of nodes) {
    const path = node.object.path;
    const rowKey = objectTreeKey(node.object);
    rows.push({ node, depth, rowKey });
    if (node.children.length > 0 && isExpanded(path)) {
      rows.push(...flattenVisibleNodes(node.children, isExpanded, depth + 1));
    }
  }
  return rows;
}

interface TreeRowProps {
  node: TreeNode;
  depth: number;
  rowKey: string;
  expanded: boolean;
  selectedPath: string | null;
  selectedKeys: Set<string>;
  draggingPath: string | null;
  dropTargetPath: string | null;
  siblingSignature: string;
  onRowSelect: (row: TreeRowSelection, event: { metaKey: boolean; shiftKey: boolean }) => void;
  onRowContextMenu?: (row: TreeRowSelection, event: React.MouseEvent) => void;
  onOpenEditor?: (path: string) => void;
  onLoadChildren?: (path: string) => void;
}

const TreeRow = memo(function TreeRow({
  node,
  depth,
  rowKey,
  expanded,
  selectedPath,
  selectedKeys,
  draggingPath,
  dropTargetPath,
  onRowSelect,
  onRowContextMenu,
  onOpenEditor,
  onLoadChildren,
}: TreeRowProps) {
  const { t } = useTranslation("explorer");
  const { toggle } = useTreeExpanded();
  const drag = useTreeDrag();
  const path = node.object.path;
  const parentPath = parentObjectPath(path);
  const hasChildren = node.children.length > 0 || isTreeContainerType(node.object.type);
  const isPrimary = selectedPath === path && !node.object.groupRef;
  const isMultiSelected = selectedKeys.has(rowKey);
  const isSelected = isMultiSelected || isPrimary;
  const siblings = parentPath && drag && !node.object.groupRef ? drag.siblingPaths(parentPath) : [];
  const draggable = Boolean(
    drag?.canReorder
    && !node.object.groupRef
    && path !== "root"
    && parentPath
    && siblings.length > 1
  );
  const isDragging = draggingPath === path;
  const isDropTarget = dropTargetPath === path && draggingPath !== path;
  const driverTreeClass = deviceDriverTreeClass(
    node.object.type,
    node.object.driverStatus,
    node.object.driverConnected,
  );

  const handleDrop = (event: React.DragEvent) => {
    event.preventDefault();
    if (!drag?.draggingPath || !parentPath || drag.draggingPath === path) {
      drag?.endDrag();
      return;
    }
    if (parentObjectPath(drag.draggingPath) !== parentPath) {
      drag.endDrag();
      return;
    }
    const fromIndex = siblings.indexOf(drag.draggingPath);
    const toIndex = siblings.indexOf(path);
    if (fromIndex === -1 || toIndex === -1 || fromIndex === toIndex) {
      drag.endDrag();
      return;
    }
    const next = [...siblings];
    next.splice(fromIndex, 1);
    next.splice(toIndex, 0, drag.draggingPath);
    drag.reorder(parentPath, next);
    drag.endDrag();
  };

  return (
    <div
      role="treeitem"
      tabIndex={0}
      aria-selected={isSelected}
      title={node.object.groupRef ? t("tree.linkTitle", { path }) : path}
      className={`tree-row ${isSelected ? "selected" : ""} ${isDragging ? "dragging" : ""} ${isDropTarget ? "drop-target" : ""} ${node.object.groupRef ? "group-ref" : ""} ${node.object.groupMemberMissing ? "missing-ref" : ""}${driverTreeClass ? ` ${driverTreeClass}` : ""}`}
      style={{ paddingLeft: `${depth * 16 + 8}px` }}
      draggable={draggable}
      onClick={(event) =>
        onRowSelect(selectionFromObject(node.object), {
          metaKey: event.metaKey || event.ctrlKey,
          shiftKey: event.shiftKey,
        })
      }
      onDoubleClick={() => {
        if (isSpecializedEditorObject(path, node.object.type, node.object.templateId)) {
          onOpenEditor?.(path);
        }
      }}
      onContextMenu={(event) => {
        event.preventDefault();
        event.stopPropagation();
        onRowContextMenu?.(selectionFromObject(node.object), event);
      }}
      onKeyDown={(event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onRowSelect(selectionFromObject(node.object), { metaKey: false, shiftKey: false });
        }
      }}
      onDragStart={(event) => {
        if (!draggable || !drag) {
          event.preventDefault();
          return;
        }
        event.dataTransfer.setData("text/plain", path);
        event.dataTransfer.effectAllowed = "move";
        drag.startDrag(path);
      }}
      onDragOver={(event) => {
        if (!drag?.draggingPath || !parentPath || node.object.groupRef) {
          return;
        }
        if (parentObjectPath(drag.draggingPath) !== parentPath) {
          return;
        }
        event.preventDefault();
        event.dataTransfer.dropEffect = "move";
        drag.setDropTarget(path);
      }}
      onDragLeave={() => {
        if (drag?.dropTargetPath === path) {
          drag.setDropTarget(null);
        }
      }}
      onDrop={handleDrop}
      onDragEnd={() => drag?.endDrag()}
    >
      <span
        className="tree-toggle"
        onClick={(e) => {
          if (hasChildren) {
            e.stopPropagation();
            if (!expanded) {
              onLoadChildren?.(path);
            }
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
          federated={node.object.federated}
        />
      </span>
      <span className="tree-label">
        {node.object.groupRef && <span className="group-ref-badge" title={t("tree.groupMember")}>↗</span>}
        {node.object.displayName}
      </span>
      <span className="tree-type">{node.object.type}</span>
    </div>
  );
}, (prev, next) =>
  prev.rowKey === next.rowKey
  && prev.node.object.displayName === next.node.object.displayName
  && prev.node.object.type === next.node.object.type
  && prev.node.object.iconId === next.node.object.iconId
  && prev.node.object.federated === next.node.object.federated
  && prev.node.object.groupRef === next.node.object.groupRef
  && prev.node.object.groupMemberMissing === next.node.object.groupMemberMissing
  && prev.node.object.driverStatus === next.node.object.driverStatus
  && prev.node.object.driverConnected === next.node.object.driverConnected
  && prev.node.children.length === next.node.children.length
  && prev.depth === next.depth
  && prev.expanded === next.expanded
  && prev.selectedPath === next.selectedPath
  && prev.selectedKeys === next.selectedKeys
  && prev.draggingPath === next.draggingPath
  && prev.dropTargetPath === next.dropTargetPath
  && prev.siblingSignature === next.siblingSignature);

export default function ObjectTree({
  nodes,
  objects,
  selectedPath,
  selectedKeys,
  onRowSelect,
  onOpenEditor,
  canReorder = false,
  onReorder,
  onLoadChildren,
  onVisibleRowKeysChange,
  bulkActions,
}: ObjectTreeProps) {
  const [expandedPaths, setExpandedPaths] = useState<Set<string>>(() => readExpandedPaths());
  const [defaultsSeeded, setDefaultsSeeded] = useState(() => readExpandedPaths().size > 0);
  const [draggingPath, setDraggingPath] = useState<string | null>(null);
  const [dropTargetPath, setDropTargetPath] = useState<string | null>(null);
  const [scrollTop, setScrollTop] = useState(0);
  const [viewportHeight, setViewportHeight] = useState(400);
  const [contextMenu, setContextMenu] = useState<TreeContextMenuState | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  const openContextMenu = useCallback((
    event: React.MouseEvent,
    context?: { path: string | null; objectType?: ObjectType },
  ) => {
    if (!bulkActions) {
      return;
    }
    event.preventDefault();
    setContextMenu({
      x: event.clientX,
      y: event.clientY,
      contextPath: context?.path ?? bulkActions.contextPath ?? null,
      contextObjectType: context?.objectType ?? bulkActions.contextObjectType,
    });
  }, [bulkActions]);

  const handleRowContextMenu = useCallback(
    (row: TreeRowSelection, event: React.MouseEvent) => {
      if (!bulkActions) {
        return;
      }
      if (!event.metaKey && !event.ctrlKey && !event.shiftKey && !bulkActions.selectedKeys.has(row.key)) {
        bulkActions.onSelectionChange(new Set([row.key]));
        onRowSelect(row, { metaKey: false, shiftKey: false });
      }
      const obj = bulkActions.objects.find((item) => item.path === row.path);
      openContextMenu(event, { path: row.path, objectType: obj?.type });
    },
    [bulkActions, onRowSelect, openContextMenu],
  );

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
    for (const path of ancestors) {
      onLoadChildren?.(path);
    }
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
  }, [selectedPath, onLoadChildren]);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) {
      return;
    }
    const updateHeight = () => setViewportHeight(el.clientHeight);
    updateHeight();
    const observer = new ResizeObserver(updateHeight);
    observer.observe(el);
    return () => observer.disconnect();
  }, [nodes.length]);

  const isExpanded = useCallback((path: string) => expandedPaths.has(path), [expandedPaths]);

  const flatRows = useMemo(
    () => flattenVisibleNodes(nodes, isExpanded),
    [nodes, isExpanded],
  );

  useEffect(() => {
    onVisibleRowKeysChange?.(flatRows.map((row) => row.rowKey));
  }, [flatRows, onVisibleRowKeysChange]);

  const siblingSignatures = useMemo(() => {
    const cache = new Map<string, string>();
    return (path: string) => {
      const parentPath = parentObjectPath(path);
      if (!parentPath) {
        return "";
      }
      const cached = cache.get(parentPath);
      if (cached !== undefined) {
        return cached;
      }
      const signature = siblingObjectPaths(parentPath, objects).join("|");
      cache.set(parentPath, signature);
      return signature;
    };
  }, [objects]);

  const startIndex = Math.max(0, Math.floor(scrollTop / ROW_HEIGHT) - OVERSCAN);
  const endIndex = Math.min(
    flatRows.length,
    Math.ceil((scrollTop + viewportHeight) / ROW_HEIGHT) + OVERSCAN,
  );
  const visibleRows = flatRows.slice(startIndex, endIndex);
  const offsetY = startIndex * ROW_HEIGHT;
  const totalHeight = flatRows.length * ROW_HEIGHT;

  const expandedApi = useMemo<TreeExpandedContextValue>(
    () => ({
      isExpanded,
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
    [isExpanded],
  );

  const dragApi = useMemo<TreeDragContextValue>(
    () => ({
      canReorder: canReorder && Boolean(onReorder),
      draggingPath,
      dropTargetPath,
      startDrag: setDraggingPath,
      endDrag: () => {
        setDraggingPath(null);
        setDropTargetPath(null);
      },
      setDropTarget: setDropTargetPath,
      reorder: (parentPath, orderedPaths) => onReorder?.(parentPath, orderedPaths),
      siblingPaths: (parentPath) => siblingObjectPaths(parentPath, objects),
    }),
    [canReorder, draggingPath, dropTargetPath, onReorder, objects],
  );

  return (
    <TreeExpandedContext.Provider value={expandedApi}>
      <TreeDragContext.Provider value={dragApi}>
        <div
          ref={containerRef}
          role="tree"
          className={`object-tree ${draggingPath ? "is-dragging" : ""}`}
          onScroll={(event) => {
            setScrollTop(event.currentTarget.scrollTop);
            setContextMenu(null);
          }}
          onContextMenu={openContextMenu}
        >
          {flatRows.length > 0 && (
            <div className="object-tree-virtual" style={{ height: totalHeight }}>
              <div className="object-tree-window" style={{ transform: `translateY(${offsetY}px)` }}>
                {visibleRows.map(({ node, depth, rowKey }) => (
                  <TreeRow
                    key={rowKey}
                    node={node}
                    depth={depth}
                    rowKey={rowKey}
                    expanded={isExpanded(node.object.path)}
                    selectedPath={selectedPath}
                    selectedKeys={selectedKeys}
                    draggingPath={draggingPath}
                    dropTargetPath={dropTargetPath}
                    siblingSignature={siblingSignatures(node.object.path)}
                    onRowSelect={onRowSelect}
                    onRowContextMenu={handleRowContextMenu}
                    onOpenEditor={onOpenEditor}
                    onLoadChildren={onLoadChildren}
                  />
                ))}
              </div>
            </div>
          )}
        </div>
        {bulkActions && (
          <TreeBulkContextMenu
            {...bulkActions}
            menu={contextMenu}
            onClose={() => setContextMenu(null)}
          />
        )}
      </TreeDragContext.Provider>
    </TreeExpandedContext.Provider>
  );
}
