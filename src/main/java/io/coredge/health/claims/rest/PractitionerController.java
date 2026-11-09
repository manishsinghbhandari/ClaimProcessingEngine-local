package io.coredge.health.claims.rest;

import io.coredge.health.claims.helpers.Helper;
import io.coredge.health.claims.models.master.PractitionerDetailsResponse;
import io.coredge.health.claims.service.PractitionerDetailsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static io.coredge.health.claims.constants.Constants.EndPoints.*;
import org.springframework.hateoas.CollectionModel;

import org.springframework.hateoas.EntityModel;


@RestController
@RequestMapping(PRACTITIONER_API_MAPPING)
@Slf4j
public class PractitionerController {

    @Autowired
    PractitionerDetailsService practitionerDetailsService;

    @GetMapping(path = PRACTITIONER_ALL_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<PractitionerDetailsResponse>> fetchAllPractitionerDetails() {
        return ResponseEntity.ok(practitionerDetailsService.fetchAllPractitionerDetails());
    }

    @GetMapping(path = PRACTITIONER_ALL_PAGE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)

    public ResponseEntity<CollectionModel<EntityModel<PractitionerDetailsResponse>>> fetchAllPractitionerDetailsPage(int page, int size, String sortBy, String direction) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Helper.getSortDirection(direction), sortBy));
        return ResponseEntity.ok(practitionerDetailsService.fetchAllPractitionerDetails(pageRequest));
    }

    @GetMapping(path = PRACTITIONER_REGNO_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<PractitionerDetailsResponse>> fetchPractitionerDetailsByRegNo( String regNo) {
        return ResponseEntity.ok(practitionerDetailsService.fetchPractitionerDetailsByRegNo(regNo));
    }
}
