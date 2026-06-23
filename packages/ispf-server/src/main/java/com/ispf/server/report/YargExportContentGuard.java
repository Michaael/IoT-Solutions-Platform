package com.ispf.server.report;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

final class YargExportContentGuard {

    private YargExportContentGuard() {
    }

    /**
     * Text-based outputs where a UTF-8 substring search is reliable.
     */
    static boolean shouldValidate(ReportExportFormat format) {
        return format == ReportExportFormat.HTML || format == ReportExportFormat.CSV;
    }

    static boolean outputMissingReportData(byte[] content, Map<String, Object> runResult) {
        if (content == null || content.length == 0) {
            return true;
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) runResult.get("rows");
        if (rows == null || rows.isEmpty()) {
            return false;
        }
        for (Map<String, Object> row : rows) {
            for (Object value : row.values()) {
                if (value == null) {
                    continue;
                }
                String text = String.valueOf(value).trim();
                if (text.length() < 3) {
                    continue;
                }
                if (binaryContainsText(content, text)) {
                    return false;
                }
            }
        }
        return true;
    }

    static boolean binaryContainsText(byte[] content, String text) {
        if (text == null || text.isBlank() || content == null || content.length == 0) {
            return false;
        }
        if (containsBytes(content, text.getBytes(StandardCharsets.UTF_8))) {
            return true;
        }
        byte[] utf16 = encodeUtf16Le(text);
        return containsBytes(content, utf16);
    }

    private static byte[] encodeUtf16Le(String text) {
        byte[] bytes = new byte[text.length() * 2];
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            bytes[index * 2] = (byte) (ch & 0xFF);
            bytes[index * 2 + 1] = (byte) ((ch >> 8) & 0xFF);
        }
        return bytes;
    }

    private static boolean containsBytes(byte[] content, byte[] needle) {
        if (needle.length == 0 || content.length < needle.length) {
            return false;
        }
        outer:
        for (int index = 0; index <= content.length - needle.length; index++) {
            for (int offset = 0; offset < needle.length; offset++) {
                if (content[index + offset] != needle[offset]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
