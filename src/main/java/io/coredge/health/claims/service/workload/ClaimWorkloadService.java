package io.coredge.health.claims.service.workload;

import io.coredge.health.claims.db.entity.UserBucket;
import io.coredge.health.claims.db.repositories.UserBucketRepository;
import io.coredge.health.claims.models.workload.ClaimWorkloadReportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClaimWorkloadService implements IClaimWorkload {

    private final UserBucketRepository userBucketRepository;

    @Transactional(readOnly = true)
    @Override
    public Page<ClaimWorkloadReportResponse> getClaimWorkloadReport(UUID claimId, Pageable pageable) {

        Page<UserBucket> history = userBucketRepository.findByClaim_ClaimIdOrderByCreationDateDesc(claimId, pageable);

        return history.map(this::mapToResponse);
    }

    private ClaimWorkloadReportResponse mapToResponse(UserBucket bucket) {
        return new ClaimWorkloadReportResponse(
                bucket.getUser().getUsername(),
                bucket.getCreationDate(),
                bucket.getStatus(),
                bucket.getClaimType(),
                bucket.getClaimStatus()
        );
    }
}
