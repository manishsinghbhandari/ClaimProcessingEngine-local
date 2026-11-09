package io.coredge.health.claims.rest.bms;

import io.coredge.health.claims.db.entity.BMS.SchemeMemberLimit;
import io.coredge.health.claims.dto.eligibility.EligibilityResponseDTO;
import io.coredge.health.claims.service.BMS.BMSEligibilityService;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static io.coredge.health.claims.constants.Constants.EndPoints.*;

@RestController
@CrossOrigin(origins = "*", maxAge = 3600)
@RequestMapping(BMS_ELIGIBILITY)
@RequiredArgsConstructor
public class BMSEligibilityController {

    private final BMSEligibilityService bmsEligibilityService;


    @PostMapping(BMS_ElIGIBILITY_PROCESS)
    public ResponseEntity<List<SchemeMemberLimit>> processEligibility(@RequestBody EligibilityResponseDTO dto) {
        return ResponseEntity.ok(bmsEligibilityService.processEligibility(dto));
    }

    @PostMapping(BMS_ELIGIBILITY_UPLOAD)
    public ResponseEntity<String> uploadEligibility(@RequestParam("file") MultipartFile file) {
        String response = bmsEligibilityService.uploadEligibilityFile(file);
        return ResponseEntity.ok(response);
    }
    

}