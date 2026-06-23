package com.ispf.server.report;

import com.ispf.server.config.ReportYargProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Component
public class LibreOfficeDocumentConverter {

    private static final Logger log = LoggerFactory.getLogger(LibreOfficeDocumentConverter.class);

    private final ReportYargProperties reportYargProperties;

    public LibreOfficeDocumentConverter(ReportYargProperties reportYargProperties) {
        this.reportYargProperties = reportYargProperties;
    }

    public byte[] convertSpreadsheetToPdf(byte[] spreadsheet, String extension) {
        return convert(spreadsheet, extension, "pdf");
    }

    public byte[] convertSpreadsheet(byte[] spreadsheet, String inputExtension, String outputExtension) {
        return convert(spreadsheet, inputExtension, outputExtension);
    }

    private byte[] convert(byte[] input, String inputExtension, String outputExtension) {
        String inputExt = normalizeExtension(inputExtension, "xlsx");
        String outputExt = normalizeExtension(outputExtension, "pdf");
        Path soffice = resolveSofficeBinary();

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("ispf-report-lo-");
            String baseName = "report";
            Path inputPath = tempDir.resolve(baseName + "." + inputExt);
            Files.write(inputPath, input);

            int timeoutSeconds = Math.max(30, reportYargProperties.getLibreOffice().getTimeoutSeconds());
            ProcessBuilder processBuilder = new ProcessBuilder(
                    soffice.toString(),
                    "--headless",
                    "--norestore",
                    "--convert-to",
                    outputExt,
                    "--outdir",
                    tempDir.toString(),
                    inputPath.toString()
            );
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            String processOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("LibreOffice conversion timed out after " + timeoutSeconds + "s");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException(
                        "LibreOffice conversion failed (exit " + process.exitValue() + "): " + processOutput
                );
            }

            Path outputPath = tempDir.resolve(baseName + "." + outputExt);
            if (!Files.isRegularFile(outputPath)) {
                throw new IllegalStateException(
                        "LibreOffice did not produce " + baseName + "." + outputExt + ": " + processOutput
                );
            }
            return Files.readAllBytes(outputPath);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LibreOffice conversion interrupted", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("LibreOffice conversion failed: " + ex.getMessage(), ex);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private Path resolveSofficeBinary() {
        String officeProgramDir = YargReportingSupport.resolveProgramPath(reportYargProperties.getLibreOffice().getPath())
                .orElse(null);
        if (officeProgramDir == null) {
            throw new IllegalArgumentException(YargReportingSupport.libreOfficeRequiredMessage());
        }
        Path soffice = Path.of(officeProgramDir, isWindows() ? "soffice.exe" : "soffice");
        if (!Files.isRegularFile(soffice)) {
            soffice = Path.of(officeProgramDir, "soffice.bin");
        }
        if (!Files.isRegularFile(soffice)) {
            throw new IllegalArgumentException("LibreOffice soffice binary not found in " + officeProgramDir);
        }
        return soffice;
    }

    private static String normalizeExtension(String extension, String fallback) {
        if (extension == null || extension.isBlank()) {
            return fallback;
        }
        return extension.trim().toLowerCase().replaceFirst("^\\.", "");
    }

    private static void deleteRecursively(Path tempDir) {
        if (tempDir == null) {
            return;
        }
        try {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            log.debug("Could not delete temp file {}", path);
                        }
                    });
        } catch (IOException ignored) {
            log.debug("Could not clean temp dir {}", tempDir);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
