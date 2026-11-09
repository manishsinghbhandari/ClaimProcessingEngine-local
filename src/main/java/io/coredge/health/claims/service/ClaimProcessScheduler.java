package io.coredge.health.claims.service;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class ClaimProcessScheduler {

    @Value("${process.scheduler.batch.size:50}")
    private int batchSize;

    @Value("${scheduler.preauth.batch-size:20}")
    private int preauthBatchSize;

    @Value("${scheduler.claim.batch-size:20}")
    private int claimBatchSize;

    private final InsuranceClaimsRepository insuranceClaimsRepository;
    private final BpmnTaskService bpmnTaskService;

    public ClaimProcessScheduler(InsuranceClaimsRepository insuranceClaimsRepository,
                                 BpmnTaskService bpmnService) {
        this.insuranceClaimsRepository = insuranceClaimsRepository;
        this.bpmnTaskService = bpmnService;
    }

    /**
     * Runs every 2 minutes:
     *  - Fetch all claims where status = QUEUED
     *  - Sends them to newProcessesForClaims()
     */
    // ----- 1. PREAUTH Scheduler -----
//    @Scheduled(cron = "${scheduler.preauth.cron}")
//    public void autoStartPreAuthQueuedClaims() {
//
//        log.info("Starting PREAUTH queued claim scheduler...");
//
//        LocalDateTime last30Days = LocalDateTime.now().minusDays(30);
//
//        Pageable pageable = PageRequest.of(0, preauthBatchSize); // always page 0
//
//        Page<UUID> preauthQueuePage =
//                insuranceClaimsRepository.findQueuedClaimIds(
//                        ExtensionStateCodeEnum.QUEUED,
//                        Constants.Use.PREAUTHORIZATION,
//                        last30Days,
//                        pageable
//                );
//
//        List<UUID> preauthQueue = preauthQueuePage.getContent();
//
//        log.info("Found {} PREAUTH claims in QUEUED state.", preauthQueue.size());
//
//        if (!preauthQueue.isEmpty()) {
//            processInBatches(preauthQueue, preauthBatchSize, "Preauthorization");
//        } else {
//            log.info("No PREAUTH queued.");
//        }
//    }
//
//    // ----- 2. CLAIM Scheduler -----
//    @Scheduled(cron = "${scheduler.claim.cron}")
//    public void autoStartClaimQueuedClaims() {
//        log.info("Starting CLAIM queued claim scheduler...");
//
//        LocalDateTime last30Days = LocalDateTime.now().minusDays(30);
//
//        Pageable pageable = PageRequest.of(0, preauthBatchSize); // always page 0
//        Page<UUID> claimQueuePage = insuranceClaimsRepository.findQueuedClaimIds(
//                ExtensionStateCodeEnum.QUEUED,
//                Constants.Use.CLAIM,
//                last30Days,
//                pageable
//        );
//
//        List<UUID> claimQueue = claimQueuePage.getContent();
//        log.info("Found {} CLAIM items in QUEUED state.", claimQueue.size());
//
//        if (!claimQueue.isEmpty()) {
//            processInBatches(claimQueue, claimBatchSize, "Claim");
//        } else {
//            log.info("No CLAIM queued.");
//        }
//    }
//
//
//    private void processInBatches(List<UUID> ids, int batchSize, String type) {
//
//        for (int i = 0; i < ids.size(); i += batchSize) {
//            int end = Math.min(i + batchSize, ids.size());
//
//            List<String> batch = ids.subList(i, end).stream()
//                    .map(UUID::toString)
//                    .toList();
//
//            log.info("Processing {} batch: records {} to {}", type, i, end);
//
//            // Call your existing Zeebe parallel processor
//            bpmnTaskService.newProcessesForClaims(batch);
//        }
//    }

}
