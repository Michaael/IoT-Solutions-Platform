import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { fetchGroupMembers, updateGroupMembers } from "../api";
import type { ObjectSummary } from "../types";
import { objectTreeKey } from "../utils/treeRowKey";
import { ObjectTreePickerDialog } from "../ui";

interface VisualGroupInspectorProps {
  path: string;
  canManage: boolean;
  allObjects: ObjectSummary[];
  onSelectPath: (path: string) => void;
  onMembersChanged?: () => void;
}

export default function VisualGroupInspector({
  path,
  canManage,
  allObjects,
  onSelectPath,
  onMembersChanged,
}: VisualGroupInspectorProps) {
  const { t } = useTranslation(["inspector", "common"]);
  const queryClient = useQueryClient();
  const [pickerOpen, setPickerOpen] = useState(false);
  const [selectedMemberPaths, setSelectedMemberPaths] = useState<Set<string>>(new Set());

  const membersQuery = useQuery({
    queryKey: ["group-members", path],
    queryFn: () => fetchGroupMembers(path),
  });

  const members = membersQuery.data ?? [];

  const memberSummaries = useMemo(() => {
    const byPath = new Map(allObjects.map((obj) => [obj.path, obj]));
    return members.map((member) => ({
      member,
      summary: byPath.get(member.path),
    }));
  }, [allObjects, members]);

  const mutateMembers = useMutation({
    mutationFn: (args: { action: "add" | "remove" | "reorder"; paths: string[] }) =>
      updateGroupMembers(path, args.action, { paths: args.paths }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["group-members", path] });
      onMembersChanged?.();
    },
  });

  const toggleMember = (memberPath: string) => {
    setSelectedMemberPaths((current) => {
      const next = new Set(current);
      if (next.has(memberPath)) {
        next.delete(memberPath);
      } else {
        next.add(memberPath);
      }
      return next;
    });
  };

  return (
    <div className="visual-group-inspector">
      <p className="hint">
        {t("visualGroup.hint")}
      </p>

      {canManage && (
        <div className="visual-group-toolbar">
          <button type="button" className="btn primary" onClick={() => setPickerOpen(true)}>
            {t("visualGroup.addObjects")}
          </button>
          <button
            type="button"
            className="btn"
            disabled={selectedMemberPaths.size === 0 || mutateMembers.isPending}
            onClick={() => {
              mutateMembers.mutate({ action: "remove", paths: [...selectedMemberPaths] });
              setSelectedMemberPaths(new Set());
            }}
          >
            {t("visualGroup.removeSelected")}
          </button>
        </div>
      )}

      {membersQuery.isLoading && <p className="sidebar-msg">{t("visualGroup.loadingMembers")}</p>}

      {!membersQuery.isLoading && members.length === 0 && (
        <p className="inspector-empty">{t("visualGroup.empty")}</p>
      )}

      <ul className="visual-group-member-list">
        {memberSummaries.map(({ member, summary }) => (
          <li
            key={objectTreeKey({
              path: member.path,
              groupRef: true,
              groupContextPath: path,
            } as ObjectSummary)}
            className={summary ? "" : "missing"}
          >
            {canManage && (
              <input
                type="checkbox"
                checked={selectedMemberPaths.has(member.path)}
                onChange={() => toggleMember(member.path)}
              />
            )}
            <button type="button" className="linkish" onClick={() => onSelectPath(member.path)}>
              {summary?.displayName ?? t("visualGroup.missing", { path: member.path })}
            </button>
            <span className="tree-type mono">{member.path}</span>
          </li>
        ))}
      </ul>

      <ObjectTreePickerDialog
        open={pickerOpen}
        title={t("visualGroup.addToGroup")}
        onClose={() => setPickerOpen(false)}
        onSelect={(memberPath) => {
          if (memberPath === path || members.some((m) => m.path === memberPath)) {
            return;
          }
          mutateMembers.mutate(
            { action: "add", paths: [memberPath] },
            { onSuccess: () => setPickerOpen(false) },
          );
        }}
      />
    </div>
  );
}
