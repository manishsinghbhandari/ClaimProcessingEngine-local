package io.coredge.health.claims.rest;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.enums.ProviderSearchType;
import io.coredge.health.claims.models.master.ProviderResponse;
import io.coredge.health.claims.models.master.SuggestionResponse;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

public interface ProviderController {

    @GetMapping(path = Constants.EndPoints.PROVIDER_MASTER_API_MAPPING, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<ProviderResponse>> fetchByProviderDetails(@RequestParam ProviderSearchType searchType, @RequestParam String searchValue);

    @GetMapping(path = Constants.EndPoints.PROVIDER_MASTER_API_MAPPING_ALL, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<ProviderResponse>> fetchAllFacility();

    @GetMapping(path = Constants.EndPoints.FACILITY_MASTER_API_MAPPING_PAGE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CollectionModel<EntityModel<ProviderResponse>>> fetchAllPageableFacility(@RequestParam(required = false) String name, @RequestParam(required = false) String fidCode,
                                                                                            @RequestParam(required = false) String level, @RequestParam(required = false) String county, @RequestParam(required = false) String regNo,
                                                                                            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
                                                                                            @RequestParam(defaultValue = "id") String sortBy, @RequestParam(defaultValue = "ASC") String direction);

    @GetMapping(path = Constants.EndPoints.PROVIDER_MASTER_API_MAPPING_ID, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CollectionModel<EntityModel<ProviderResponse>>> fetchByProviderDetailsUsingID(@RequestParam String searchValue, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size, @RequestParam(defaultValue = "id") String sortBy, @RequestParam(defaultValue = "ASC") String direction);

    @GetMapping(path = Constants.EndPoints.PROVIDER_MASTER_API_SEARCH, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<SuggestionResponse>> searchAllFacility(@RequestParam String searchValue);

    @GetMapping(path = Constants.EndPoints.PROVIDER_MASTER_API_GET_BY_ID, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ProviderResponse> getById(@PathVariable String id);

}
