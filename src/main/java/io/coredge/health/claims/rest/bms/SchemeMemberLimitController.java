package io.coredge.health.claims.rest.bms;

import io.coredge.health.claims.db.entity.BMS.ClaimTransaction;
import io.coredge.health.claims.dto.ProcessClaimDTO;
import io.coredge.health.claims.service.BMS.SchemeMemberLimitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static io.coredge.health.claims.constants.Constants.EndPoints.SCHEME_MEMBER_LIMIT;
import static io.coredge.health.claims.constants.Constants.EndPoints.SCHEME_MEMBER_LIMIT_PROCESS_CLAIM;

@RestController
@CrossOrigin(origins = "*", maxAge = 3600)
@RequestMapping(SCHEME_MEMBER_LIMIT)
@RequiredArgsConstructor
public class SchemeMemberLimitController {

    private final SchemeMemberLimitService schemeMemberLimitService;

    @PostMapping(SCHEME_MEMBER_LIMIT_PROCESS_CLAIM)
    public ResponseEntity<ClaimTransaction> processClaim(
           @Valid @RequestBody ProcessClaimDTO request
    ) {
        ClaimTransaction transaction = schemeMemberLimitService.processClaim(
                request.getClaimId(),
                request.getBenefitCode(),
                request.getAmount(),
                request.getSchemeCode()
        );
        return ResponseEntity.ok(transaction);
    }
}