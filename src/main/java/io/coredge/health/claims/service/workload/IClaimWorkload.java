package io.coredge.health.claims.service.workload;

import io.coredge.health.claims.models.workload.ClaimWorkloadReportResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface IClaimWorkload {

    Page<ClaimWorkloadReportResponse> getClaimWorkloadReport(UUID claimId, Pageable pageable);
}
