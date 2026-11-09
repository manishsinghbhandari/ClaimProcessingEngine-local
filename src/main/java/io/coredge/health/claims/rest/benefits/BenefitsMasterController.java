package io.coredge.health.claims.rest.benefits;


import io.coredge.health.claims.helpers.Helper;
import io.coredge.health.claims.models.benefits.BenefitsMasterDropDownResponse;
import io.coredge.health.claims.models.benefits.BenefitsMasterRequestDTO;
import io.coredge.health.claims.models.benefits.BenefitsMasterResponseDTO;
import io.coredge.health.claims.service.benefits.BenefitsMasterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


import static io.coredge.health.claims.constants.Constants.AdminEndPoints.*;

/**
 * REST controller for handling Benefits master data.
 * Exposes endpoints for retrieving all benefits and metadata dropdowns.
 */
@RestController
@RequestMapping(path = BENEFITS_ENDPOINT_PREFIX)
@RequiredArgsConstructor
@CrossOrigin
public class BenefitsMasterController {

    private final BenefitsMasterService benefitsMasterService;

    /**
     * Get metadata for dropdowns (e.g. provider payment mechanisms, bed types, etc.).
     *
     * @return BenefitsMasterDropDownResponse
     */
    @GetMapping(path = BENEFITS_ENDPOINT_DROP_DOWNS)
    public ResponseEntity<BenefitsMasterDropDownResponse> getBenefitsMeta() {
        return ResponseEntity.ok(benefitsMasterService.getBenefitsDropdown());
    }

    @PutMapping(path = BENEFITS_ENDPOINT_ID)
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<BenefitsMasterResponseDTO> updateBenefitsMaster(@PathVariable("id") Long id, @Valid @RequestBody BenefitsMasterRequestDTO request) {
        return ResponseEntity.ok(benefitsMasterService.updateAllowedFields(id, request));
    }

    @GetMapping(path = BENEFITS_ENDPOINT_ID)
    public ResponseEntity<BenefitsMasterResponseDTO> getBenefitsDetails(@PathVariable("id") Long id) {
        return ResponseEntity.ok(benefitsMasterService.getBenefit(id));
    }

    @GetMapping(path = BENEFITS_ENDPOINT_SEARCH)
    public ResponseEntity<CollectionModel<EntityModel<BenefitsMasterResponseDTO>>> fetch(
            @RequestParam(required = false) String interventionCode,
            @RequestParam(required = false) String code,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "interventionCode") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {

        Sort sort = Sort.by(Helper.getSortDirection(direction), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        return ResponseEntity.ok(benefitsMasterService.filterByInterventionOrCode(interventionCode, code, pageable)
        );
    }

}
