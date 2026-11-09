package io.coredge.health.claims.utils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;

public class ExcelGenerator {

    /**
     * Generates an Excel file as a ByteArrayInputStream.
     *
     * @param headers   List of column headers.
     * @param data      List of data to be included in the sheet.
     * @param extractor Function to map each data item to a row of cell values.
     * @param <T>       The type of data in the list.
     * @return ByteArrayInputStream containing the Excel file.
     */
    public static <T> ByteArrayInputStream generateExcel(
            List<String> headers,
            List<T> data,
            Function<T, List<Object>> extractor
    ) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Data");

            // Create header row
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
            }

            // Populate data rows
            int rowNum = 1;
            for (T item : data) {
                Row row = sheet.createRow(rowNum++);
                List<Object> cellValues = extractor.apply(item);

                for (int i = 0; i < cellValues.size(); i++) {
                    Object value = cellValues.get(i);
                    Cell cell = row.createCell(i);

                    // Set cell value based on the object's type
                    if (value instanceof Number) {
                        cell.setCellValue(((Number) value).doubleValue());
                    } else if (value instanceof String) {
                        cell.setCellValue((String) value);
                    } else if (value instanceof Boolean) {
                        cell.setCellValue((Boolean) value);
                    } else if (value != null) {
                        cell.setCellValue(value.toString());
                    }
                }
            }

            // Auto-size columns
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            // Write to output stream
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Error while generating Excel file", e);
        }
    }
}

