import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { resolvePlatformTimeZone } from "../api/platformTimeZone";

interface ResolvedTimeZoneBadgeProps {
  objectPath: string;
}

export default function ResolvedTimeZoneBadge({ objectPath }: ResolvedTimeZoneBadgeProps) {
  const { t } = useTranslation("inspector");
  const tzQuery = useQuery({
    queryKey: ["platform-timezone-resolve", objectPath],
    queryFn: () => resolvePlatformTimeZone(objectPath),
    enabled: Boolean(objectPath?.trim()),
    staleTime: 60_000,
  });

  if (tzQuery.isLoading) {
    return <p className="hint">{t("timeZone.resolving")}</p>;
  }

  if (tzQuery.error || !tzQuery.data?.timeZone) {
    return (
      <span className="resolved-timezone-badge is-error" role="status">
        {t("timeZone.error")}
      </span>
    );
  }

  return (
    <span className="resolved-timezone-badge" role="status">
      {t("timeZone.resolved", { zone: tzQuery.data.timeZone })}
    </span>
  );
}
