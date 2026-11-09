package io.coredge.health.claims.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.coredge.health.claims.db.repositories.ClaimReviewSummaryRepository;
import io.coredge.health.claims.enums.AdjudicationParamsMessage;
import io.coredge.health.claims.db.entity.ClaimLineItem;
import io.coredge.health.claims.db.repositories.LineItemRepository;
import io.coredge.health.claims.enums.ClaimType;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.models.RuleInputDTO;
import io.coredge.health.claims.models.report.ClaimReviewSummary;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ReportService {

    private final LineItemRepository lineItemRepository;
    private final RuleProcessingService ruleProcessingService;
    private final ClaimReviewSummaryRepository claimReviewSummaryRepository;

    @Autowired
    public ReportService(LineItemRepository lineItemRepository, RuleProcessingService ruleProcessingService, ClaimReviewSummaryRepository claimReviewSummaryRepository, ClaimReviewSummaryRepository claimReviewSummaryRepository1) {
        this.lineItemRepository = lineItemRepository;
        this.ruleProcessingService = ruleProcessingService;
        this.claimReviewSummaryRepository = claimReviewSummaryRepository1;
    }

    public ByteArrayInputStream getReport(String fileName) {
        ClaimReviewSummary summary = claimReviewSummaryRepository.findByFileName(fileName)
                .orElseThrow(() -> new BadRequestException("File not found"));
        byte[] decodedBytes = Base64.getDecoder().decode(summary.getBase64File());
        return new ByteArrayInputStream(decodedBytes);
    }


    @Async("virtualThreadExecutor")
    public void getClaimReviewReport(ExtensionStateCodeEnum status, ClaimType type , boolean isPhc , LocalDateTime startDate, LocalDateTime endDate, String fileName) {

        List<ClaimLineItem> claims = lineItemRepository.findClaimsByStatus(status, type.getDisplayName() , isPhc , startDate, endDate);
        log.info("Downloading claim review report...{}", claims.size());
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Claim Review");
            // Create header row
            Row headerRow = sheet.createRow(0);
            List<String> headers = getHeaders();
            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
            }

            int rowIndex = 1;
            List<String> rules = new ArrayList<>();
            List<String> messages = new ArrayList<>();
            for (ClaimLineItem claim : claims) {
               rules.clear();
               messages.clear();
                if (StringUtils.isNotEmpty(claim.getAdjudicationRule()) && StringUtils.isNotEmpty(claim.getAdjudicationParams())) {
                    try {
                        Map<String, RuleInputDTO> response = ruleProcessingService.processAdjudicationParamsRule(claim);
                        response.entrySet().stream().filter(entry -> entry.getValue() != null && "FAIL".equals(entry.getValue().getResult()))
                                .filter(entry -> AdjudicationParamsMessage.fromValue(entry.getKey()) != null)
                                .forEach(entry -> {
                                    rules.add(AdjudicationParamsMessage.fromValue(entry.getKey()).getRule());
                                    messages.add(AdjudicationParamsMessage.createMessage(AdjudicationParamsMessage.fromValue(entry.getKey()), entry.getValue().getRule().toString(), entry.getValue().getInput().toString()));
                                });

                        if(messages.isEmpty()){
                            messages.add("PASSED");
                            rules.add("PASSED");
                        }
                    } catch (JsonProcessingException e) {
                        log.warn("JsonProcessingException for Claim Item with id{} : Exception {}", claim.getId(), e.getMessage());
                        messages.add("JsonProcessingException" + e.getMessage());
                        rules.add("Json_Processing_Exception");
                    }
                }
                else {
                    continue;
                }

                if (messages.isEmpty()) {
                    continue;
                }
                Row row = sheet.createRow(rowIndex);
                row.createCell(0).setCellValue(rowIndex);
                row.createCell(1).setCellValue(claim.getClaim().getClaimId() != null ? claim.getClaim().getClaimId().toString() : "");
                row.createCell(2).setCellValue(claim.getClaim().getClaimNumber()!=null ? claim.getClaim().getClaimNumber().toString() : "");
                row.createCell(3).setCellValue(claim.getClaim().getCreationDate()!= null ? claim.getClaim().getCreationDate().format(DateTimeFormatter.ISO_DATE_TIME): "");
                row.createCell(4).setCellValue(claim.getClaim().getDateFrom() != null ? claim.getClaim().getDateFrom().format(DateTimeFormatter.ISO_DATE_TIME) : "");
                row.createCell(5).setCellValue(claim.getClaim().getDateTo() != null ? claim.getClaim().getDateTo().format(DateTimeFormatter.ISO_DATE_TIME) : "");
                row.createCell(6).setCellValue(claim.getClaim().getProvider() != null ? claim.getClaim().getProvider().getMflCode() : "");
                row.createCell(7).setCellValue(claim.getClaim().getProvider() != null ? claim.getClaim().getProvider().getName() : "");
                row.createCell(8).setCellValue(claim.getApprovedPrice() != null ? claim.getApprovedPrice().toString() : "");
                row.createCell(9).setCellValue(claim.getNetValue() != null ? claim.getNetValue().toString() : "");
                row.createCell(10).setCellValue(claim.getClaim().getStatus() != null ? claim.getClaim().getStatus().getDisplayName() : "");
                row.createCell(11).setCellValue(claim.getClaim().getPatient() != null ? claim.getClaim().getPatient().getNationalId() : "");
                row.createCell(12).setCellValue(claim.getClaim().getPatient() != null ? claim.getClaim().getPatient().getCrId() : "");
                row.createCell(13).setCellValue(String.join("\n", messages));
                row.createCell(14).setCellValue(String.join(" | ", rules));

                rowIndex++;
            }
//            for (int i = 0; i < headers.size(); i++) sheet.autoSizeColumn(i);
//            sheet.createFreezePane(0, 1);
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                workbook.write(outputStream);
                String base64Encoded = com.nimbusds.jose.util.Base64.encode(outputStream.toByteArray()).toString();
                claimReviewSummaryRepository.save(new ClaimReviewSummary(null, status, startDate, endDate, fileName, base64Encoded));
            }
        } catch (Exception e) {
            log.warn("Error generating Excel report: ", e);
            throw new BadRequestException("Failed to generate Excel file: " + e.getMessage());
        }
    }

    private static @NotNull List<String> getHeaders() {
        return List.of(
                "S.No.",
                "Claim Id",
                "Claim Number",
                "Created Date",
                "Bill Start Date",
                "Bill End Date",
                "MFL Code",
                "Provider Name",
                "Approved Price",
                "Net Value",
                "Status",
                "Patient National ID",
                "Patient CR_ID",
                "Reason",
                "Rules");
    }
}
