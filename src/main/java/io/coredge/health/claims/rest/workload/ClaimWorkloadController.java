package io.coredge.health.claims.rest.workload;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.helpers.Helper;
import io.coredge.health.claims.models.workload.ClaimWorkloadReportResponse;
import io.coredge.health.claims.service.workload.IClaimWorkload;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(Constants.WORKLOAD.CLAIM_WORKLOAD)
@RequiredArgsConstructor
public class ClaimWorkloadController {

    private final IClaimWorkload claimWorkload;

    @GetMapping(Constants.WORKLOAD.CLAIM_WORKLOAD_REPORT)
    public ResponseEntity<Page<ClaimWorkloadReportResponse>> getClaimWorkloadReport(
            @PathVariable UUID claimId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "creationDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Helper.getSortDirection(direction), sortBy));

        return ResponseEntity.ok(claimWorkload.getClaimWorkloadReport(claimId, pageable)
        );
    }
}
