import { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import ObjectTree from "../components/ObjectTree";
import { useLazyObjectTree } from "../hooks/useLazyObjectTree";
import type { ObjectType } from "../types";
import { selectionFromObject } from "../utils/treeRowKey";
import Modal from "./Modal";

export interface ObjectTreePickerDialogProps {
  open: boolean;
  title?: string;
  onClose: () => void;
  onSelect: (path: string) => void;
  filterTypes?: ObjectType[];
  rootPath?: string;
  multi?: boolean;
}

function pathUnderRoot(path: string, rootPath?: string): boolean {
  if (!rootPath?.trim()) {
    return true;
  }
  const root = rootPath.trim();
  return path === root || path.startsWith(`${root}.`);
}

function matchesFilter(type: ObjectType, filterTypes?: ObjectType[]): boolean {
  if (!filterTypes || filterTypes.length === 0) {
    return true;
  }
  return filterTypes.includes(type);
}

export default function ObjectTreePickerDialog({
  open,
  title,
  onClose,
  onSelect,
  filterTypes,
  rootPath,
  multi = false,
}: ObjectTreePickerDialogProps) {
  const { t } = useTranslation(["common", "objectTree"]);
  const { tree, objects, loadChildren, treeLoadError } = useLazyObjectTree(open);
  const [selectedPath, setSelectedPath] = useState<string | null>(null);
  const [selectedKeys, setSelectedKeys] = useState<Set<string>>(new Set());
  const [filterQuery, setFilterQuery] = useState("");

  const filteredTree = useMemo(() => {
    const q = filterQuery.trim().toLowerCase();
    if (!q && !rootPath && (!filterTypes || filterTypes.length === 0)) {
      return tree;
    }
    const allowedPaths = new Set<string>();
    for (const obj of objects) {
      if (!pathUnderRoot(obj.path, rootPath)) {
        continue;
      }
      if (!matchesFilter(obj.type, filterTypes)) {
        continue;
      }
      if (q && !obj.path.toLowerCase().includes(q) && !obj.displayName.toLowerCase().includes(q)) {
        continue;
      }
      allowedPaths.add(obj.path);
      let parent = obj.path;
      while (parent.includes(".")) {
        parent = parent.slice(0, parent.lastIndexOf("."));
        allowedPaths.add(parent);
      }
    }
    const filterNodes = (nodes: typeof tree): typeof tree =>
      nodes
        .filter((node) => allowedPaths.has(node.object.path))
        .map((node) => ({
          object: node.object,
          children: filterNodes(node.children),
        }));
    return filterNodes(tree);
  }, [tree, objects, filterQuery, rootPath, filterTypes]);

  const confirmSelection = useCallback(() => {
    if (!selectedPath) {
      return;
    }
    onSelect(selectedPath);
    onClose();
  }, [onSelect, onClose, selectedPath]);

  const handleRowSelect = useCallback(
    (row: ReturnType<typeof selectionFromObject>, event: { metaKey: boolean; shiftKey: boolean }) => {
      if (!row.path) {
        return;
      }
      const obj = objects.find((item) => item.path === row.path);
      if (obj && filterTypes?.length && !matchesFilter(obj.type, filterTypes)) {
        return;
      }
      if (multi) {
        setSelectedKeys((current) => {
          const next = new Set(current);
          if (next.has(row.key)) {
            next.delete(row.key);
          } else {
            next.add(row.key);
          }
          return next;
        });
      }
      setSelectedPath(row.path);
      if (!multi && event.metaKey === false && event.shiftKey === false) {
        onSelect(row.path);
        onClose();
      }
    },
    [objects, filterTypes, multi, onClose, onSelect],
  );

  return (
    <Modal
      open={open}
      title={title ?? t("objectPath.browseTree")}
      onClose={onClose}
      wide
      className="modal-object-tree-picker"
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            {t("action.cancel")}
          </button>
          <button
            type="button"
            className="btn primary"
            disabled={!selectedPath}
            onClick={confirmSelection}
          >
            {t("modal.select")}
          </button>
        </>
      }
    >
      <input
        type="search"
        className="object-tree-picker-search"
        placeholder={t("action.search")}
        value={filterQuery}
        onChange={(event) => setFilterQuery(event.target.value)}
        autoFocus
      />
      {treeLoadError && <p className="op-alert op-alert-error">{treeLoadError}</p>}
      <div className="object-tree-picker-body">
        <ObjectTree
          nodes={filteredTree}
          objects={objects}
          selectedPath={selectedPath}
          selectedKeys={selectedKeys}
          onRowSelect={handleRowSelect}
          onLoadChildren={(path) => void loadChildren(path)}
        />
      </div>
      {filterTypes && filterTypes.length > 0 && (
        <p className="hint object-tree-picker-hint">
          {t("objectPath.filterHint", { types: filterTypes.join(", ") })}
        </p>
      )}
    </Modal>
  );
}
