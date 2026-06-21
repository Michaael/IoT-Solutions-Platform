package com.ispf.server.platform.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Component
public class GitHubReleaseClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    public GitHubReleaseClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public Optional<GitHubRelease> fetchLatestRelease(
            String owner,
            String repo,
            String jarAssetName,
            String webConsoleAssetName
    ) throws IOException, InterruptedException {
        URI uri = URI.create("https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "ispf-server")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() >= 400) {
            throw new IOException("GitHub API returned HTTP " + response.statusCode() + ": " + response.body());
        }
        JsonNode root = objectMapper.readTree(response.body());
        String tagName = text(root, "tag_name");
        if (tagName == null || tagName.isBlank()) {
            return Optional.empty();
        }
        GitHubRelease release = new GitHubRelease(
                tagName,
                text(root, "name"),
                text(root, "html_url"),
                text(root, "body"),
                parseInstant(text(root, "published_at")),
                findAssetUrl(root, jarAssetName),
                findAssetUrl(root, webConsoleAssetName)
        );
        return Optional.of(release);
    }

    public void downloadAsset(String url, java.nio.file.Path destination) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .header("Accept", "application/octet-stream")
                .header("User-Agent", "ispf-server")
                .GET()
                .build();
        HttpResponse<java.io.InputStream> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );
        if (response.statusCode() >= 400) {
            throw new IOException("Failed to download release asset: HTTP " + response.statusCode());
        }
        java.nio.file.Files.createDirectories(destination.getParent());
        try (java.io.InputStream input = response.body()) {
            java.nio.file.Files.copy(input, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String findAssetUrl(JsonNode root, String assetName) {
        JsonNode assets = root.get("assets");
        if (assets == null || !assets.isArray()) {
            return null;
        }
        for (JsonNode asset : assets) {
            if (assetName.equals(text(asset, "name"))) {
                return text(asset, "browser_download_url");
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }

    public record GitHubRelease(
            String tagName,
            String name,
            String htmlUrl,
            String body,
            Instant publishedAt,
            String jarDownloadUrl,
            String webConsoleDownloadUrl
    ) {
    }
}
