package io.coredge.health.claims.models.reportdashboard;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

public record ReportDashboardFilterOptions(
        List<String> fid_code,
        List<String> statusCode,
        List<String> levels,
        List<String> accessPoints,
        List<String> counties,
        List<String> facilityNames,
        List<String> claimTypes,
        List<String> sources,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime startDate,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime endDate
) {

    public ReportDashboardFilterOptions(List<String> statusCode, List<String> claimTypes, List<String> levels, List<String> accessPoints, List<String> counties,List<String> sources) {
        this(null, statusCode, levels, accessPoints, counties, null, claimTypes, sources, null, null);
    }
}
