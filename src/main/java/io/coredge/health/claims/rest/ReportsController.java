package io.coredge.health.claims.rest;

import io.coredge.health.claims.enums.ClaimType;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.service.ReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;

import static io.coredge.health.claims.constants.Constants.EndPoints.REPORT_API_PATH;

@RestController
@RequestMapping(REPORT_API_PATH)
@Slf4j
public class ReportsController {

    private final ReportService service;

    public ReportsController(ReportService service) {
        this.service = service;
    }

    @Transactional(timeout = 30 * 60) //60 * 60
    @GetMapping("/{fileName}")
    public ResponseEntity<?> downloadReport(@PathVariable("fileName") String fileName) {
        try {
            if(!fileName.contains(".xlsx"))
                fileName = fileName + ".xlsx";

            ByteArrayInputStream excelData = service.getReport(fileName);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(new InputStreamResource(excelData));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("An error occurred while generating the Excel file: " + e.getMessage());
        }
    }

    @GetMapping("/{status}/{type}/{isPhc}/{startDate}/{endDate}")
    public ResponseEntity<?> downloadClaimByStatus(@PathVariable("status") ExtensionStateCodeEnum status,
                                                    @PathVariable("type") ClaimType type,
                                                   @PathVariable("isPhc") boolean isPhc,
                                                    @PathVariable("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
                                                    @PathVariable("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        String filename = "Report_"+ type.getDisplayName() +"_"+ status.getDisplayName() + "_" + isPhc + "_" + System.currentTimeMillis() + ".xlsx";
        service.getClaimReviewReport(status , type, isPhc ,startDate , endDate, filename);
        return ResponseEntity.ok().header(filename).body("File generated successfully :"+filename);
    }

}
