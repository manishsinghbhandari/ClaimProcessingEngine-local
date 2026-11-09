package io.coredge.health.claims.rest.reportdashboard;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.enums.*;
import io.coredge.health.claims.exception.app.DataNotFoundException;
import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.models.reportdashboard.ClaimReportExcelResponse;
import io.coredge.health.claims.models.reportdashboard.ClaimReportPdfResponse;
import io.coredge.health.claims.models.reportdashboard.ClaimReportUiResponse;
import io.coredge.health.claims.models.reportdashboard.ReportDashboardFilterOptions;
import io.coredge.health.claims.service.reportdashboard.ReportDashboardService;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;

import java.util.Arrays;
import java.util.List;
import java.util.Map;


@Slf4j
@RestController
@RequestMapping(Constants.EndPoints.REPORT_DASHBOARD)
public class ReportDashboardController {

    private final ReportDashboardService reportDashboardService;

    @Autowired
    private PagedResourcesAssembler<ClaimReportUiResponse> pagedAssembler;

    public ReportDashboardController(ReportDashboardService reportDashboardService) {
        this.reportDashboardService = reportDashboardService;
    }

    @GetMapping(Constants.EndPoints.REPORT_DASHBOARD_SUGGEST_FIDCODE)
    public ResponseEntity<Page<String>> suggestFidCode(@RequestParam
                                                       @Size(min = 6, message = "Search Value must be atleast 6 characters")
                                                       String searchValue) {
        return ResponseEntity.ok(reportDashboardService.searchAllFidCode(searchValue));
    }

    @GetMapping(Constants.EndPoints.REPORT_DASHBOARD_SUBCOUNTY)
    public ResponseEntity<List<String>> getSubCounty() {
        return ResponseEntity.ok(reportDashboardService.getAllSubCounty());
    }

    @GetMapping(Constants.EndPoints.REPORT_DASHBOARD_All)
    public ResponseEntity<ReportDashboardFilterOptions> getAllFilters() {
        List<String> statusCode = Arrays.stream(ExtensionStateCodeEnum.values()).map(Enum::name).toList();
        List<String> claimType = reportDashboardService.getAllClaimTypes();
        List<String> levels = reportDashboardService.searchAllLevels();
        List<String> access = Arrays.stream(SubTypeEnum.values()).map(Enum::name).toList();
        List<String> county = reportDashboardService.getAllCounty();
        List<String> sources = reportDashboardService.getAllSources();

        ReportDashboardFilterOptions reportDashboardFilterOptions =
                new ReportDashboardFilterOptions(statusCode, claimType, levels, access, county,sources);

        return ResponseEntity.ok(reportDashboardFilterOptions);
    }

    @PostMapping(Constants.EndPoints.REPORT_DASHBOARD_DATA)
    public ResponseEntity<PagedModel<EntityModel<ClaimReportUiResponse>>> getClaimsReport(
            @RequestBody ReportDashboardFilterOptions filter, Pageable pageable) {
        Page<ClaimReportUiResponse> page = reportDashboardService.getClaimsUiReportPage(
                filter, pageable
        );
        return ResponseEntity.ok(pagedAssembler.toModel(page));
    }

    @PostMapping(Constants.EndPoints.REPORT_DASHBOARD_DOWNLOAD_PDF)
    public ResponseEntity<InputStreamResource> downloadPdf(@RequestBody ReportDashboardFilterOptions filterRequest) {
        List<ClaimReportPdfResponse> data = reportDashboardService.getClaimsForPdf(filterRequest);
        ByteArrayInputStream in = reportDashboardService.generatePdfReport(data);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=claim_report.pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_PDF)
                .body(new InputStreamResource(in));
    }

    @PostMapping(Constants.EndPoints.REPORT_DASHBOARD_DOWNLOAD_EXCEL)
    public ResponseEntity<Resource> downloadExcel(@RequestBody ReportDashboardFilterOptions filterRequest) {
        long startTime = System.currentTimeMillis();

        try {
            List<ClaimReportExcelResponse> data = reportDashboardService.getClaimsForExcel(filterRequest);

            Map<String, Object> fileResult = reportDashboardService.generateExcelFileStream(data);
            ByteArrayInputStream fileStream = (ByteArrayInputStream) fileResult.get("stream");
            String fileName = (String) fileResult.get("fileName");

            BufferedInputStream bufferedStream = new BufferedInputStream(fileStream);
            ByteArrayResource resource = new ByteArrayResource(bufferedStream.readAllBytes());

            long endTime = System.currentTimeMillis();
            log.info("Excel export completed in {} ms, file: {}", (endTime - startTime), fileName);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (DataNotFoundException de){
            throw de;
        } catch (Exception ex) {
            long endTime = System.currentTimeMillis();
            log.error("Excel export failed after {} ms: {}", (endTime - startTime), ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}
