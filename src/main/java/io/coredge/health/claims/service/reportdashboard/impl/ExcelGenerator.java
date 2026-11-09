package io.coredge.health.claims.service.reportdashboard.impl;

import io.coredge.health.claims.models.reportdashboard.ClaimReportExcelResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
public class ExcelGenerator {

    public static ByteArrayInputStream generate(List<ClaimReportExcelResponse> data) {
        if(data.size() > 1_000_000) {
            throw new IllegalArgumentException("Data size exceeds the maximum limit of 1,000,000 rows.");
        }
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Claim Report");
            CreationHelper creationHelper = workbook.getCreationHelper();

            // Styles
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row header = sheet.createRow(0);

            String[] headers = {"Claim ID", "Facility Name", "Facility FR Code", "Patient Name", "Patient CR ID", "Patient National ID",
                    "Facility Level", "Claimed Amount", "Approved Amount", "Status", "Claim Date", "Claim Type",
                    "County", "Sub County", "Source"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            //DATA
            int rowIdx = 1;
            for (ClaimReportExcelResponse item : data) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(item.getClaimId() != null ? item.getClaimId() : "");
                row.createCell(1).setCellValue(item.getFacilityName() != null ? item.getFacilityName() : "");
                row.createCell(2).setCellValue(item.getFacilityFrCode() != null ? item.getFacilityFrCode() : "");
                row.createCell(3).setCellValue(item.getPatientName() != null ? item.getPatientName() : "");
                row.createCell(4).setCellValue(item.getPatientCrId() != null ? item.getPatientCrId() : "");
                row.createCell(5).setCellValue(item.getPatientNationalId() != null ? item.getPatientNationalId() : "");
                row.createCell(6).setCellValue(item.getFacilityLevel() != null ? item.getFacilityLevel() : "");
                row.createCell(7).setCellValue(item.getClaimedAmount() != null ? item.getClaimedAmount().doubleValue() : 0.0);
                row.createCell(8).setCellValue(item.getApprovedAmount() != null ? item.getApprovedAmount().doubleValue() : 0.0);
                row.createCell(9).setCellValue(item.getStatus() != null ? item.getStatus() : "");
                row.createCell(10).setCellValue(item.getClaimDate() != null ? item.getClaimDate().toString() : "");
                row.createCell(11).setCellValue(item.getClaimType() != null ? item.getClaimType() : "");
                row.createCell(12).setCellValue(item.getCounty() != null ? item.getCounty() : "");
                row.createCell(13).setCellValue(item.getSubCounty() != null ? item.getSubCounty() : "");
                row.createCell(14).setCellValue(item.getSource() != null ? item.getSource() : "");
            }
            workbook.write(out);
            byte[] excelBytes = out.toByteArray();
            // If size > 5MB, zip it
            if (excelBytes.length > 5 * 1024 * 1024) {
                ByteArrayOutputStream zipOut = new ByteArrayOutputStream();
                try (ZipOutputStream zos = new ZipOutputStream(zipOut)) {
                    ZipEntry zipEntry = new ZipEntry("claim_report.xlsx");
                    zos.putNextEntry(zipEntry);
                    zos.write(excelBytes);
                    zos.closeEntry();
                }
                return new ByteArrayInputStream(zipOut.toByteArray());
            }
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Excel export failed: Error during file generation.", e);
        }
    }

    public static void writeToWorkbook(List<ClaimReportExcelResponse> data, OutputStream out) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Claim Report");

            // Styles
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // Headers
            Row header = sheet.createRow(0);
            String[] headers = {
                    "Claim ID", "Facility Name", "Facility FR Code", "Patient Name", "Patient CR ID", "Patient National ID",
                    "Facility Level", "Claimed Amount", "Approved Amount", "Status", "Claim Date", "Claim Type",
                    "County", "Sub County", "Source"
            };
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            int rowIdx = 1;
            for (ClaimReportExcelResponse item : data) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(item.getClaimId() != null ? item.getClaimId() : "");
                row.createCell(1).setCellValue(item.getFacilityName() != null ? item.getFacilityName() : "");
                row.createCell(2).setCellValue(item.getFacilityFrCode() != null ? item.getFacilityFrCode() : "");
                row.createCell(3).setCellValue(item.getPatientName() != null ? item.getPatientName() : "");
                row.createCell(4).setCellValue(item.getPatientCrId() != null ? item.getPatientCrId() : "");
                row.createCell(5).setCellValue(item.getPatientNationalId() != null ? item.getPatientNationalId() : "");
                row.createCell(6).setCellValue(item.getFacilityLevel() != null ? item.getFacilityLevel() : "");
                row.createCell(7).setCellValue(item.getClaimedAmount() != null ? item.getClaimedAmount().doubleValue() : 0.0);
                row.createCell(8).setCellValue(item.getApprovedAmount() != null ? item.getApprovedAmount().doubleValue() : 0.0);
                row.createCell(9).setCellValue(item.getStatus() != null ? item.getStatus() : "");
                row.createCell(10).setCellValue(item.getClaimDate() != null ? item.getClaimDate().toString() : "");
                row.createCell(11).setCellValue(item.getClaimType() != null ? item.getClaimType() : "");
                row.createCell(12).setCellValue(item.getCounty() != null ? item.getCounty() : "");
                row.createCell(13).setCellValue(item.getSubCounty() != null ? item.getSubCounty() : "");
                row.createCell(14).setCellValue(item.getSource() != null ? item.getSource() : "");
            }
            workbook.write(out);
        }
    }
}

