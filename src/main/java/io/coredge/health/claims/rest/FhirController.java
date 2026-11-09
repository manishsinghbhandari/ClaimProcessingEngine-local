package io.coredge.health.claims.rest;

import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.models.claims.ClaimDataResponse;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

import java.util.List;

public interface FhirController {

    ResponseEntity<String> getByClaimId(@RequestParam String claimId);

    ResponseEntity<String> getByClaimResponseId(@RequestParam String claimResponseId);

    ResponseEntity<String> getByPatientId(@RequestParam String patientId);

    ResponseEntity<String> getByOrganizationId(@RequestParam String mflCode);

    ResponseEntity<String> getClaimResponseByClaimId(@RequestParam String claimId);

    ResponseEntity<String> getAllCodeSystems();

    ResponseEntity<String> getCodeSystemById(@PathVariable String id);

    ResponseEntity<String> createOrUpdateCodeSystem(@PathVariable String id, @RequestBody String codeSystemJson);

    ResponseEntity<String> updateLineItemsHistory(@RequestParam String use, @RequestParam int count);

    ResponseEntity<String> claimCallBack(@RequestParam String claimId);

}
