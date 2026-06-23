import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchObject } from "../api";
import type { ObjectSummary } from "../types";
import FederationBindPanel from "./FederationBindPanel";

interface ObjectFederationBindSectionProps {
  path: string;
  canManage: boolean;
  /** When object is already loaded, skip extra fetch. */
  object?: ObjectSummary | null;
  className?: string;
}

export default function ObjectFederationBindSection({
  path,
  canManage,
  object: objectProp,
  className,
}: ObjectFederationBindSectionProps) {
  const { t } = useTranslation("federation");
  const queryClient = useQueryClient();

  const objectQuery = useQuery({
    queryKey: ["object", path],
    queryFn: () => fetchObject(path),
    enabled: path !== "root" && !objectProp,
  });

  const object = objectProp ?? objectQuery.data;

  if (path === "root" || !object) {
    if (objectQuery.isLoading && !objectProp) {
      return <p className="hint">{t("loading")}</p>;
    }
    return null;
  }

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ["object", path] });
    queryClient.invalidateQueries({ queryKey: ["object-editor", path] });
    queryClient.invalidateQueries({ queryKey: ["variables", path] });
    queryClient.invalidateQueries({ queryKey: ["objects"] });
  };

  return (
    <div className={className}>
      <FederationBindPanel object={object} canManage={canManage} onChanged={refresh} />
    </div>
  );
}
