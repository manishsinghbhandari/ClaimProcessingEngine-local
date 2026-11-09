package io.coredge.health.claims.service.reportdashboard;

import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.models.reportdashboard.ClaimReportExcelResponse;
import io.coredge.health.claims.models.reportdashboard.ClaimReportPdfResponse;
import io.coredge.health.claims.models.reportdashboard.ClaimReportUiResponse;
import io.coredge.health.claims.models.reportdashboard.ReportDashboardFilterOptions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public interface ReportDashboardService {

    Page<String> searchAllFidCode(String searchvalue);

    List<String> searchAllLevels();

    List<String> getAllCounty();

    List<String> getAllSubCounty();

    Page<ClaimReportUiResponse> getClaimsUiReportPage(ReportDashboardFilterOptions filterRequest, Pageable pageable);

    List<ClaimReportExcelResponse> getClaimsForExcel(ReportDashboardFilterOptions filterRequest);

    List<ClaimReportPdfResponse> getClaimsForPdf(ReportDashboardFilterOptions filterRequest);

    ByteArrayInputStream generateExcelReport(List<ClaimReportExcelResponse> data);

    ByteArrayInputStream generatePdfReport(List<ClaimReportPdfResponse> data);

    List<String> getAllSources();

    Map<String, Object> generateExcelFileStream(List<ClaimReportExcelResponse> data);

    List<String> getAllClaimTypes();
}
