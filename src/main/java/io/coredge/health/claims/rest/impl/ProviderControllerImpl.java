package io.coredge.health.claims.rest.impl;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.enums.ProviderSearchType;
import io.coredge.health.claims.helpers.Helper;
import io.coredge.health.claims.models.master.ProviderResponse;
import io.coredge.health.claims.models.master.SuggestionResponse;
import io.coredge.health.claims.rest.ProviderController;
import io.coredge.health.claims.service.ProviderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping(Constants.EndPoints.MASTER_API_REQUEST_PATH)
public class ProviderControllerImpl implements ProviderController {

    @Autowired
    private ProviderService providerService;


    @Override
    public ResponseEntity<List<ProviderResponse>> fetchByProviderDetails(ProviderSearchType searchType, @RequestParam String searchValue) {
        return ResponseEntity.ok(providerService.fetchProviderDetails(searchType, searchValue));
    }

    @Override
    public ResponseEntity<List<ProviderResponse>> fetchAllFacility() {
        return ResponseEntity.ok(providerService.getProviderDetailsList());
    }

    @Override
    public ResponseEntity<CollectionModel<EntityModel<ProviderResponse>>> fetchByProviderDetailsUsingID(String searchValue, int page, int size, String sortBy, String direction) {
        return ResponseEntity.ok(providerService.fetchAllProviderDetailsByLevelPage(searchValue, PageRequest.of(page, size, Sort.by(Helper.getSortDirection(direction), sortBy))));
    }

    @Override
    public ResponseEntity<CollectionModel<EntityModel<ProviderResponse>>> fetchAllPageableFacility(String name, String fidCode, String level, String county, String regNo, int page, int size, String sortBy, String direction) {
        return ResponseEntity.ok(providerService.fetchAllProviderDetailsPage(name, fidCode, level, county, regNo,  PageRequest.of(page, size, Helper.createSortObject(sortBy, direction, true))));
    }

    @Override
    public ResponseEntity<List<SuggestionResponse>> searchAllFacility(String searchValue) {
        return ResponseEntity.ok(providerService.searchAllFacility(searchValue));
    }

    @Override
    public ResponseEntity<ProviderResponse> getById(String id) {
        return ResponseEntity.ok(providerService.findById(id));
    }

}
