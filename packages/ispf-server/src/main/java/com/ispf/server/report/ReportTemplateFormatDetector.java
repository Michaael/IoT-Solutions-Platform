package com.ispf.server.report;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ReportTemplateFormatDetector {

    private ReportTemplateFormatDetector() {
    }

    /**
     * Resolves template format from file content and name. File content wins over the UI dropdown
     * so that uploading {@code report.xls} with {@code xlsx} selected still works.
     */
    public static String resolve(String declaredFormat, String originalFilename, byte[] content) {
        String fromFilename = fromFilename(originalFilename);
        String fromZip = isZip(content) ? fromZipPackage(content) : null;
        String fromOle = isOleCompound(content) ? fromFilename : null;

        String detected = firstNonBlank(fromZip, fromOle, fromFilename);
        if (detected != null) {
            return detected;
        }
        if (declaredFormat == null || declaredFormat.isBlank()) {
            throw new IllegalArgumentException("Cannot detect template format — choose format explicitly");
        }
        return declaredFormat.trim().toLowerCase(Locale.ROOT);
    }

    static String fromFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return switch (filename.substring(dot + 1).toLowerCase(Locale.ROOT)) {
            case "xlsx" -> "xlsx";
            case "xls" -> "xls";
            case "docx" -> "docx";
            case "doc" -> "doc";
            case "html", "htm" -> "html";
            default -> null;
        };
    }

    private static String fromZipPackage(byte[] content) {
        boolean hasWorkbook = false;
        boolean hasDocument = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("xl/")) {
                    hasWorkbook = true;
                }
                if (name.startsWith("word/")) {
                    hasDocument = true;
                }
            }
        } catch (IOException ex) {
            return null;
        }
        if (hasWorkbook && !hasDocument) {
            return "xlsx";
        }
        if (hasDocument && !hasWorkbook) {
            return "docx";
        }
        return null;
    }

    private static boolean isZip(byte[] content) {
        return content.length >= 2 && content[0] == 'P' && content[1] == 'K';
    }

    private static boolean isOleCompound(byte[] content) {
        return content.length >= 4
                && (content[0] & 0xFF) == 0xD0
                && (content[1] & 0xFF) == 0xCF
                && (content[2] & 0xFF) == 0x11
                && (content[3] & 0xFF) == 0xE0;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
