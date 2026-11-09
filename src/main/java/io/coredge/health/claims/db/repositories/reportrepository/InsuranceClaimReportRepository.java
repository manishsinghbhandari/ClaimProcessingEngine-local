package io.coredge.health.claims.db.repositories.reportrepository;

import io.coredge.health.claims.models.reportdashboard.ClaimReportExcelResponse;
import io.coredge.health.claims.models.reportdashboard.ClaimReportPdfResponse;
import io.coredge.health.claims.models.reportdashboard.ClaimReportUiResponse;
import io.coredge.health.claims.models.reportdashboard.ReportDashboardFilterOptions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.time.LocalDateTime;

public interface InsuranceClaimReportRepository {
    Page<ClaimReportUiResponse> findClaimsForUiReportPage(ReportDashboardFilterOptions filterRequest, Pageable pageable);
    List<ClaimReportExcelResponse> findClaimsForExcelReport(ReportDashboardFilterOptions filterRequest);
    List<ClaimReportPdfResponse> findClaimsForPdfReport(ReportDashboardFilterOptions filterRequest);
}
