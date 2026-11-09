package io.coredge.health.claims.service.reportdashboard.impl;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import io.coredge.health.claims.exception.app.DataNotFoundException;
import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.exception.models.ErrorCode;
import io.coredge.health.claims.models.reportdashboard.ClaimReportPdfResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

@Slf4j
public class PdfGenerator {

    public static ByteArrayInputStream generate(List<ClaimReportPdfResponse> data) {
        if (data.isEmpty()) {
            throw new DataNotFoundException("No data available for the provided filters.");
        }
        if (data.size() > 1_000_000) {
            throw new RuntimeException("PDF export failed: Cannot export more than 1 million records.");
        }
        Document document = new Document(PageSize.A4.rotate());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA);

            // ✅ Ensure column count matches header + data
            PdfPTable table = new PdfPTable(10);
            table.setWidthPercentage(100);
            table.setHeaderRows(1);

            String[] headers = {
                    "Claim ID", "Facility Name", "Claimed Amount", "Approved Amount", "Status",
                    "Claim Date", "Claim Type", "County", "Sub County", "Source"
            };

            for (String col : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(col, headerFont));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            for (ClaimReportPdfResponse item : data) {
                try {
                    table.addCell(new Phrase(item.getClaimId() != null ? item.getClaimId() : "N/A", bodyFont));
                    table.addCell(new Phrase(item.getFacilityName() != null ? item.getFacilityName() : "N/A", bodyFont));
                    table.addCell(new Phrase(item.getClaimedAmount() != null ? item.getClaimedAmount().toPlainString() : "0.00", bodyFont));
                    table.addCell(new Phrase(item.getApprovedAmount() != null ? item.getApprovedAmount().toPlainString() : "0.00", bodyFont));
                    table.addCell(new Phrase(item.getStatus() != null ? item.getStatus() : "N/A", bodyFont));
                    table.addCell(new Phrase(item.getClaimDate() != null ? item.getClaimDate().toString() : "N/A", bodyFont));
                    table.addCell(new Phrase(item.getClaimType() != null ? item.getClaimType() : "N/A", bodyFont));
                    table.addCell(new Phrase(item.getCounty() != null ? item.getCounty() : "N/A", bodyFont));
                    table.addCell(new Phrase(item.getSubCounty() != null ? item.getSubCounty() : "N/A", bodyFont));
                    table.addCell(new Phrase(item.getSource() != null ? item.getSource() : "N/A", bodyFont));
                } catch (Exception e) {
                    log.error("Failed to map PDF row: {}", item, e);
                }
            }

            document.add(table);
            document.close();
            return new ByteArrayInputStream(out.toByteArray());

        } catch (DocumentException e) {
            log.error("PDF generation failed", e);
            throw new RuntimeException("Failed to generate PDF report", e);
        }
    }
}
