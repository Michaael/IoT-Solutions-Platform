package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.platform.update")
public class PlatformUpdateProperties {

    /** Enable periodic GitHub release checks and in-console update prompts. */
    private boolean checkEnabled = true;

    /** Allow downloading release assets and restarting via apply script (Linux VPS). */
    private boolean applyEnabled = false;

    private String githubOwner = "Michaael";

    private String githubRepo = "IoT-Solutions-Platform";

    /** Minimum interval between GitHub API checks. */
    private long checkIntervalMs = 3_600_000L;

    private String jarAssetName = "ispf-server.jar";

    private String webConsoleAssetName = "web-console.zip";

    private String stagingDir = "/opt/ispf/staging";

    private String applyScript = "/opt/ispf/bin/apply-platform-update.sh";

    public boolean isCheckEnabled() {
        return checkEnabled;
    }

    public void setCheckEnabled(boolean checkEnabled) {
        this.checkEnabled = checkEnabled;
    }

    public boolean isApplyEnabled() {
        return applyEnabled;
    }

    public void setApplyEnabled(boolean applyEnabled) {
        this.applyEnabled = applyEnabled;
    }

    public String getGithubOwner() {
        return githubOwner;
    }

    public void setGithubOwner(String githubOwner) {
        this.githubOwner = githubOwner;
    }

    public String getGithubRepo() {
        return githubRepo;
    }

    public void setGithubRepo(String githubRepo) {
        this.githubRepo = githubRepo;
    }

    public long getCheckIntervalMs() {
        return checkIntervalMs;
    }

    public void setCheckIntervalMs(long checkIntervalMs) {
        this.checkIntervalMs = checkIntervalMs;
    }

    public String getJarAssetName() {
        return jarAssetName;
    }

    public void setJarAssetName(String jarAssetName) {
        this.jarAssetName = jarAssetName;
    }

    public String getWebConsoleAssetName() {
        return webConsoleAssetName;
    }

    public void setWebConsoleAssetName(String webConsoleAssetName) {
        this.webConsoleAssetName = webConsoleAssetName;
    }

    public String getStagingDir() {
        return stagingDir;
    }

    public void setStagingDir(String stagingDir) {
        this.stagingDir = stagingDir;
    }

    public String getApplyScript() {
        return applyScript;
    }

    public void setApplyScript(String applyScript) {
        this.applyScript = applyScript;
    }
}
