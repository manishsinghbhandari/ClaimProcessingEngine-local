package io.coredge.health.claims.rest.bms;

import io.coredge.health.claims.db.entity.BMS.SchemeDetail;
import io.coredge.health.claims.dto.SchemeDetailResponseDTO;
import io.coredge.health.claims.dto.BenefitDTO;
import io.coredge.health.claims.dto.JobGroupDTO;
import io.coredge.health.claims.dto.SchemeDetailDTO;
import io.coredge.health.claims.service.BMS.SchemeDetailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static io.coredge.health.claims.constants.Constants.EndPoints.*;

@RestController
@CrossOrigin(origins = "*", maxAge = 3600)
@RequestMapping(BMS_SCHEME_DETAILS)
@RequiredArgsConstructor
public class SchemeDetailController {

    private final SchemeDetailService schemeDetailService;

    @GetMapping
    public Page<SchemeDetail> getAllSchemeDetails(Pageable pageable) {
        return schemeDetailService.findAll(pageable);
    }

    @GetMapping(BMS_SCHEME_DETAILS_ID)
    public ResponseEntity<SchemeDetailResponseDTO> getSchemeDetailById(@PathVariable Long id) {
        return schemeDetailService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<SchemeDetail> createSchemeDetail(@Valid @RequestBody SchemeDetailDTO dto) {
        return ResponseEntity.ok(schemeDetailService.createOrUpdateSchemeDetail(dto));
    }

    @PutMapping(BMS_SCHEME_DETAILS_UPDATE_ID)
    public ResponseEntity<SchemeDetail> updateSchemeDetail(@PathVariable Long id, @Valid @RequestBody SchemeDetailDTO dto) {
        dto.setId(id);
        if (schemeDetailService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(schemeDetailService.createOrUpdateSchemeDetail(dto));
    }

    @DeleteMapping(BMS_SCHEME_DETAILS_DELETE_ID)
    public ResponseEntity<Void> deleteSchemeDetail(@PathVariable Long id) {
        if (schemeDetailService.findById(id).isPresent()) {
            schemeDetailService.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping(BMS_SCHEME_DETAILS_BENEFITS_ID)
    public List<BenefitDTO> getBenefitsBySchemeId(@PathVariable Long id) {
        return schemeDetailService.getBenefitsBySchemeId(id);
    }

    @GetMapping(BMS_SCHEME_DETAILS_JOB_GROUPS_ID)
    public List<JobGroupDTO> getJobGroupsBySchemeId(@PathVariable Long id) {
        return schemeDetailService.getJobGroupsBySchemeId(id);
    }
}
