package com.ispf.server.report;

import com.haulmont.yarg.formatters.factory.DefaultFormatterFactory;
import com.haulmont.yarg.formatters.impl.doc.connector.OfficeIntegration;
import com.haulmont.yarg.formatters.impl.doc.connector.OfficeIntegrationAPI;
import com.haulmont.yarg.loaders.factory.DefaultLoaderFactory;
import com.haulmont.yarg.loaders.impl.JsonDataLoader;
import com.haulmont.yarg.reporting.Reporting;
import com.ispf.server.config.ReportYargProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

final class YargReportingSupport {

    private static final Logger log = LoggerFactory.getLogger(YargReportingSupport.class);

    private YargReportingSupport() {
    }

    static Reporting createReporting(ReportYargProperties properties) {
        DefaultFormatterFactory formatterFactory = new DefaultFormatterFactory();
        resolveLibreOffice(properties).ifPresent(office -> {
            formatterFactory.setOfficeIntegration(office);
            log.info("YARG LibreOffice integration enabled for PDF conversion");
        });

        Reporting reporting = new Reporting();
        reporting.setFormatterFactory(formatterFactory);
        reporting.setLoaderFactory(new DefaultLoaderFactory().setJsonDataLoader(new JsonDataLoader()));
        return reporting;
    }

    static Optional<OfficeIntegrationAPI> resolveLibreOffice(ReportYargProperties properties) {
        ReportYargProperties.LibreOffice config = properties.getLibreOffice();
        Optional<String> path = resolveProgramPath(config.getPath());
        if (path.isEmpty()) {
            log.debug("LibreOffice program path not found — PDF export from Excel/Word templates unavailable");
            return Optional.empty();
        }

        List<Integer> ports = config.getPorts();
        if (ports == null || ports.isEmpty()) {
            ports = List.of(8100, 8101);
        }
        Integer[] portArray = ports.toArray(Integer[]::new);

        OfficeIntegration office = new OfficeIntegration(path.get(), portArray);
        office.setTimeoutInSeconds(config.getTimeoutSeconds());
        office.setDisplayDeviceAvailable(config.isDisplayDeviceAvailable());
        log.info("YARG LibreOffice path={} ports={}", path.get(), ports);
        return Optional.of(office);
    }

    static Optional<String> resolveProgramPath(String configuredPath) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path path = Path.of(configuredPath.trim());
            if (Files.isRegularFile(path.resolve("soffice")) || Files.isRegularFile(path.resolve("soffice.bin"))) {
                return Optional.of(path.toString());
            }
            log.warn("Configured LibreOffice path has no soffice binary: {}", path);
            return Optional.empty();
        }
        for (String candidate : List.of(
                "/usr/lib/libreoffice/program",
                "/usr/lib64/libreoffice/program",
                "C:/Program Files/LibreOffice/program",
                "C:/Program Files (x86)/LibreOffice/program")) {
            Path path = Path.of(candidate);
            if (Files.isRegularFile(path.resolve("soffice")) || Files.isRegularFile(path.resolve("soffice.bin"))) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    static boolean isLibreOfficeRequiredError(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("libre/open office")
                || lower.contains("libreoffice")
                || lower.contains("openoffice");
    }

    static String libreOfficeRequiredMessage() {
        return "PDF export from Excel/Word YARG templates requires LibreOffice on the server "
                + "(ispf.reports.yarg.libre-office.path or install libreoffice-nogui). "
                + "Use XLSX or HTML for table export without LibreOffice.";
    }
}
