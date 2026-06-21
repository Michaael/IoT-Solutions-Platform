package com.ispf.server.platform.update;

import com.ispf.server.config.PlatformUpdateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.info.BuildProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class PlatformUpdateService {

    private static final Logger log = LoggerFactory.getLogger(PlatformUpdateService.class);

    private final PlatformUpdateProperties properties;
    private final GitHubReleaseClient releaseClient;
    private final Optional<BuildProperties> buildProperties;
    private final AtomicReference<PlatformUpdateStatus> status = new AtomicReference<>();

    public PlatformUpdateService(
            PlatformUpdateProperties properties,
            GitHubReleaseClient releaseClient,
            Optional<BuildProperties> buildProperties
    ) {
        this.properties = properties;
        this.releaseClient = releaseClient;
        this.buildProperties = buildProperties;
        this.status.set(PlatformUpdateStatus.idle(
                properties.isCheckEnabled(),
                properties.isApplyEnabled(),
                currentVersion()
        ));
    }

    public PlatformUpdateStatus getStatus() {
        PlatformUpdateStatus current = status.get();
        if (current.checkedAt() == null && properties.isCheckEnabled()) {
            return refreshCheck();
        }
        return current;
    }

    public PlatformUpdateStatus refreshCheck() {
        String currentVersion = currentVersion();
        if (!properties.isCheckEnabled()) {
            PlatformUpdateStatus previous = status.get();
            PlatformUpdateStatus disabled = new PlatformUpdateStatus(
                    false,
                    properties.isApplyEnabled(),
                    currentVersion,
                    null,
                    false,
                    null,
                    null,
                    null,
                    null,
                    Instant.now(),
                    "Проверка обновлений отключена",
                    previous.applyState(),
                    previous.applyMessage(),
                    previous.applyStartedAt()
            );
            status.set(disabled);
            return disabled;
        }

        PlatformUpdateStatus previous = status.get();
        try {
            Optional<GitHubReleaseClient.GitHubRelease> release = releaseClient.fetchLatestRelease(
                    properties.getGithubOwner(),
                    properties.getGithubRepo(),
                    properties.getJarAssetName(),
                    properties.getWebConsoleAssetName()
            );
            if (release.isEmpty()) {
                PlatformUpdateStatus noRelease = new PlatformUpdateStatus(
                        true,
                        properties.isApplyEnabled(),
                        currentVersion,
                        null,
                        false,
                        null,
                        "https://github.com/" + properties.getGithubOwner() + "/" + properties.getGithubRepo() + "/releases",
                        null,
                        null,
                        Instant.now(),
                        "На GitHub пока нет опубликованных релизов",
                        previous.applyState(),
                        previous.applyMessage(),
                        previous.applyStartedAt()
                );
                status.set(noRelease);
                return noRelease;
            }

            GitHubReleaseClient.GitHubRelease latest = release.get();
            String latestVersion = PlatformVersionSupport.normalizeVersion(latest.tagName());
            boolean updateAvailable = PlatformVersionSupport.isUpdateAvailable(currentVersion, latestVersion);
            PlatformUpdateStatus next = new PlatformUpdateStatus(
                    true,
                    properties.isApplyEnabled(),
                    currentVersion,
                    latestVersion,
                    updateAvailable,
                    latest.name(),
                    latest.htmlUrl(),
                    latest.body(),
                    latest.publishedAt(),
                    Instant.now(),
                    null,
                    previous.applyState(),
                    previous.applyMessage(),
                    previous.applyStartedAt()
            );
            status.set(next);
            if (updateAvailable) {
                log.info(
                        "Platform update available: current={} latest={} url={}",
                        currentVersion,
                        latestVersion,
                        latest.htmlUrl()
                );
            }
            return next;
        } catch (Exception error) {
            log.warn("Failed to check GitHub releases: {}", error.getMessage());
            PlatformUpdateStatus failed = new PlatformUpdateStatus(
                    true,
                    properties.isApplyEnabled(),
                    currentVersion,
                    previous.latestVersion(),
                    previous.updateAvailable(),
                    previous.releaseName(),
                    previous.releaseUrl(),
                    previous.releaseNotes(),
                    previous.publishedAt(),
                    Instant.now(),
                    error.getMessage(),
                    previous.applyState(),
                    previous.applyMessage(),
                    previous.applyStartedAt()
            );
            status.set(failed);
            return failed;
        }
    }

    public PlatformUpdateStatus applyLatestUpdate() throws IOException {
        if (!properties.isApplyEnabled()) {
            throw new IllegalStateException("Автообновление отключено на этом сервере (ispf.platform.update.apply-enabled=false)");
        }

        PlatformUpdateStatus current = getStatus();
        if (!current.updateAvailable()) {
            throw new IllegalStateException("Нет доступной новой версии для установки");
        }
        if (!"IDLE".equals(current.applyState()) && !"FAILED".equals(current.applyState())) {
            throw new IllegalStateException("Обновление уже выполняется: " + current.applyState());
        }

        GitHubReleaseClient.GitHubRelease release;
        try {
            release = releaseClient.fetchLatestRelease(
                    properties.getGithubOwner(),
                    properties.getGithubRepo(),
                    properties.getJarAssetName(),
                    properties.getWebConsoleAssetName()
            ).orElseThrow(() -> new IllegalStateException("GitHub release not found"));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Release lookup interrupted", error);
        }

        if (release.jarDownloadUrl() == null || release.webConsoleDownloadUrl() == null) {
            throw new IllegalStateException(
                    "Release must include assets "
                            + properties.getJarAssetName()
                            + " and "
                            + properties.getWebConsoleAssetName()
            );
        }

        Path stagingRoot = Path.of(properties.getStagingDir(), PlatformVersionSupport.normalizeVersion(release.tagName()));
        Path jarPath = stagingRoot.resolve(properties.getJarAssetName());
        Path webConsolePath = stagingRoot.resolve(properties.getWebConsoleAssetName());
        Path applyScript = Path.of(properties.getApplyScript());

        status.set(copyStatus(current, "DOWNLOADING", "Загрузка релиза " + release.tagName(), Instant.now()));

        try {
            Files.createDirectories(stagingRoot);
            releaseClient.downloadAsset(release.jarDownloadUrl(), jarPath);
            releaseClient.downloadAsset(release.webConsoleDownloadUrl(), webConsolePath);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            status.set(copyStatus(current, "FAILED", error.getMessage(), Instant.now()));
            throw new IOException("Download interrupted", error);
        } catch (IOException error) {
            status.set(copyStatus(current, "FAILED", error.getMessage(), Instant.now()));
            throw error;
        }

        if (!Files.isRegularFile(applyScript)) {
            status.set(copyStatus(current, "FAILED", "Apply script not found: " + applyScript, Instant.now()));
            throw new IllegalStateException("Apply script not found: " + applyScript);
        }

        status.set(copyStatus(current, "RESTARTING", "Перезапуск сервера с версией " + release.tagName(), Instant.now()));
        launchApplyScript(applyScript, stagingRoot);
        return status.get();
    }

    @Scheduled(fixedDelayString = "${ispf.platform.update.check-interval-ms:3600000}")
    public void scheduledCheck() {
        if (!properties.isCheckEnabled()) {
            return;
        }
        refreshCheck();
    }

    private void launchApplyScript(Path applyScript, Path stagingRoot) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(
                "bash",
                applyScript.toString(),
                stagingRoot.toString()
        );
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(stagingRoot.resolve("apply.log").toFile()));
        builder.redirectError(ProcessBuilder.Redirect.appendTo(stagingRoot.resolve("apply.log").toFile()));
        builder.start();
        log.info("Started platform update apply script for staging {}", stagingRoot);
    }

    private PlatformUpdateStatus copyStatus(
            PlatformUpdateStatus current,
            String applyState,
            String applyMessage,
            Instant applyStartedAt
    ) {
        return new PlatformUpdateStatus(
                current.checkEnabled(),
                current.applyEnabled(),
                current.currentVersion(),
                current.latestVersion(),
                current.updateAvailable(),
                current.releaseName(),
                current.releaseUrl(),
                current.releaseNotes(),
                current.publishedAt(),
                current.checkedAt(),
                current.checkError(),
                applyState,
                applyMessage,
                applyStartedAt
        );
    }

    private String currentVersion() {
        return PlatformVersionSupport.currentVersion(buildProperties);
    }
}
