import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  applyPlatformUpdate,
  checkPlatformUpdateNow,
  fetchPlatformUpdateStatus,
} from "../api/platformUpdate";

export default function PlatformUpdateBanner() {
  const queryClient = useQueryClient();

  const statusQuery = useQuery({
    queryKey: ["platform-update"],
    queryFn: fetchPlatformUpdateStatus,
    refetchInterval: (query) => {
      const state = query.state.data?.applyState;
      if (state === "DOWNLOADING" || state === "RESTARTING") {
        return 3000;
      }
      return 15 * 60_000;
    },
    retry: 1,
  });

  const checkMutation = useMutation({
    mutationFn: checkPlatformUpdateNow,
    onSuccess: (data) => {
      queryClient.setQueryData(["platform-update"], data);
    },
  });

  const applyMutation = useMutation({
    mutationFn: applyPlatformUpdate,
    onSuccess: (data) => {
      queryClient.setQueryData(["platform-update"], data);
    },
  });

  const status = statusQuery.data;
  if (!status) {
    return null;
  }

  const applying = status.applyState === "DOWNLOADING" || status.applyState === "RESTARTING";
  const failed = status.applyState === "FAILED";
  const showBanner = status.updateAvailable || applying || failed;

  if (!showBanner && !status.checkError) {
    return null;
  }

  const handleApply = () => {
    const version = status.latestVersion ?? "новой версии";
    const confirmed = window.confirm(
      `Установить ISPF ${version} и перезапустить сервер?\n\nКонсоль будет недоступна 20–40 секунд.`
    );
    if (!confirmed) {
      return;
    }
    applyMutation.mutate();
  };

  return (
    <div
      className={`platform-update-banner ${
        failed ? "platform-update-banner-error" : applying ? "platform-update-banner-busy" : ""
      }`}
    >
      <div className="platform-update-banner-body">
        {applying && (
          <>
            <strong>Обновление платформы…</strong>
            <span>{status.applyMessage ?? "Перезапуск сервера"}</span>
          </>
        )}
        {!applying && failed && (
          <>
            <strong>Ошибка обновления</strong>
            <span>{status.applyMessage ?? "Не удалось применить релиз"}</span>
          </>
        )}
        {!applying && !failed && status.updateAvailable && (
          <>
            <strong>Доступно обновление ISPF</strong>
            <span>
              Текущая версия {status.currentVersion} → {status.latestVersion}
              {status.releaseName ? ` (${status.releaseName})` : ""}
            </span>
          </>
        )}
        {!applying && !failed && !status.updateAvailable && status.checkError && (
          <>
            <strong>Проверка обновлений</strong>
            <span>{status.checkError}</span>
          </>
        )}
      </div>
      <div className="platform-update-banner-actions">
        {status.updateAvailable && status.applyEnabled && !applying && (
          <button
            type="button"
            className="btn primary small"
            disabled={applyMutation.isPending}
            onClick={handleApply}
          >
            Обновить и перезапустить
          </button>
        )}
        {status.updateAvailable && !status.applyEnabled && !applying && status.releaseUrl && (
          <a className="btn small" href={status.releaseUrl} target="_blank" rel="noreferrer">
            Открыть релиз
          </a>
        )}
        {!applying && (
          <button
            type="button"
            className="btn small"
            disabled={checkMutation.isPending || statusQuery.isFetching}
            onClick={() => checkMutation.mutate()}
          >
            Проверить снова
          </button>
        )}
      </div>
    </div>
  );
}
