package com.ispf.server.report;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YARG's {@code JsonMap} treats dots in map keys as nested paths, so Excel placeholders like
 * {@code ${Band1.DEVICEPATH}} never resolve (containsKey passes, get returns null). Word
 * formatters split band prefixes; {@link com.haulmont.yarg.formatters.impl.XLSFormatter} does not.
 */
final class XlsTemplatePlaceholderNormalizer {

    private static final Pattern BAND1_PLACEHOLDER = Pattern.compile(
            "\\$\\{Band1\\.([A-Za-z0-9_]+)\\}",
            Pattern.CASE_INSENSITIVE
    );

    private XlsTemplatePlaceholderNormalizer() {
    }

    static byte[] normalize(byte[] content) {
        if (content == null || content.length == 0) {
            return content;
        }
        try (HSSFWorkbook workbook = new HSSFWorkbook(new ByteArrayInputStream(content))) {
            boolean changed = false;
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                for (Row row : sheet) {
                    if (row == null) {
                        continue;
                    }
                    for (Cell cell : row) {
                        if (cell == null || cell.getCellType() != CellType.STRING) {
                            continue;
                        }
                        String value = cell.getStringCellValue();
                        if (value == null || !value.contains("${Band1.")) {
                            continue;
                        }
                        String normalized = normalizeText(value);
                        if (!normalized.equals(value)) {
                            cell.setCellValue(normalized);
                            changed = true;
                        }
                    }
                }
            }
            if (!changed) {
                return content;
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to normalize Excel template placeholders: " + ex.getMessage(), ex);
        }
    }

    static String normalizeText(String value) {
        Matcher matcher = BAND1_PLACEHOLDER.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement("${" + matcher.group(1).toUpperCase() + "}"));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
