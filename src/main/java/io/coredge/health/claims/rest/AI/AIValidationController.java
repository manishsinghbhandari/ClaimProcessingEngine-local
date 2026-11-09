package io.coredge.health.claims.rest.AI;

import io.coredge.health.claims.helpers.Helper;
import io.coredge.health.claims.models.AI.AIValidationRequest;
import io.coredge.health.claims.models.AI.AIValidationResponse;
import io.coredge.health.claims.models.AI.BulkValidationMappingRequest;
import io.coredge.health.claims.models.benefits.BenefitsMasterDropDownResponse;
import io.coredge.health.claims.service.AI.AIValidationService;
import io.coredge.health.claims.service.benefits.BenefitsMasterFieldRegistry;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.List;

import static io.coredge.health.claims.constants.Constants.EndPoints.*;

@RestController
@RequestMapping(AI_VALIDATION_BASE_ENDPOINT)
@AllArgsConstructor
public class AIValidationController {

    private final AIValidationService aiValidationService;
    private final BenefitsMasterFieldRegistry  benefitsMasterFieldRegistry;

    @PostMapping
    public ResponseEntity<AIValidationResponse> createAIValidation(@Valid @RequestBody AIValidationRequest request) {
        AIValidationResponse aiValidationResponse = aiValidationService.createAIValidation(request);
        return ResponseEntity.ok(aiValidationResponse);
    }

    @PutMapping(AI_VALIDATION_ID)
    public ResponseEntity<AIValidationResponse> updateAIValidation(
            @PathVariable Long id,
            @Valid @RequestBody AIValidationRequest request) {

        AIValidationResponse aIValidationResponse = aiValidationService.updateAIValidation(id, request);
        if (aIValidationResponse == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(aIValidationResponse);
    }

    @GetMapping(AI_VALIDATION_ID)
    public ResponseEntity<AIValidationResponse> getAIValidation(@PathVariable Long id) {
        AIValidationResponse aiValidation = aiValidationService.getAIValidation(id);
        return ResponseEntity.ok(aiValidation);
    }

    @GetMapping(AI_VALIDATION_DROP_DOWN)
    public ResponseEntity<List<BenefitsMasterDropDownResponse.KeyValueResponse>> getDropdown() {
        List<BenefitsMasterDropDownResponse.KeyValueResponse> dropdown = aiValidationService.getAIValidationDropdown();
        return ResponseEntity.ok(dropdown);
    }


    @GetMapping
    public ResponseEntity<CollectionModel<EntityModel<AIValidationResponse>>> getAllAIValidations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Helper.getSortDirection(direction), sortBy));
        CollectionModel<EntityModel<AIValidationResponse>> response = aiValidationService.getAllAIValidations(pageable);
        return ResponseEntity.ok(response);
    }


    @DeleteMapping(AI_VALIDATION_ID)
    public ResponseEntity<Void> deleteAIValidation(@PathVariable Long id) {
        boolean deleted = aiValidationService.deleteAIValidation(id);
        if (deleted) {
            return ResponseEntity.status(HttpStatus.OK).build();
        } else {
            return ResponseEntity.status(HttpStatus.NO_CONTENT)
                    .build();
        }
    }

    @PostMapping(AI_VALIDATION_BULK_MAPPING)
    public ResponseEntity<String> bulkUploadAIValidation(@Valid @RequestBody BulkValidationMappingRequest request) {
        benefitsMasterFieldRegistry.bulkUploadValidationMapping(request);
        String str = String.format("Shared Request for Bulk upload of Mapping Benefits %s with Validations %s", request.getBenefit(), request.getValidation());
        return ResponseEntity.ok(str);
    }

}
