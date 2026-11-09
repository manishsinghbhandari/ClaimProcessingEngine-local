package io.coredge.health.claims.rest.impl;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.helpers.Helper;
import io.coredge.health.claims.models.BenefitMastersDTO;
import io.coredge.health.claims.models.master.BenefitMasterResponse;
import io.coredge.health.claims.models.master.CategoryMasterResponse;
import io.coredge.health.claims.models.master.CountyMasterResponse;
import io.coredge.health.claims.models.master.PractitionerDetailsResponse;
import io.coredge.health.claims.models.master.CountyOptionResponse;
import io.coredge.health.claims.models.master.UserCountyAssignmentRequest;
import io.coredge.health.claims.models.master.UserCountyAssignmentResponse;
import io.coredge.health.claims.models.master.UserCountySummaryResponse;
import io.coredge.health.claims.rest.MasterController;
import io.coredge.health.claims.service.BenefitMasterService;
import io.coredge.health.claims.service.CategoryMasterService;
import io.coredge.health.claims.service.CountyMasterService;
import io.coredge.health.claims.service.PractitionerDetailsService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Optional;

@RestController
@RequestMapping(Constants.EndPoints.MASTER_API_REQUEST_PATH)
public class MasterControllerImpl implements MasterController {

    @Autowired
    PractitionerDetailsService practitionerDetailsService;

    @Autowired
    private BenefitMasterService benefitMasterService;

    @Autowired
    private CategoryMasterService categoryService;

    @Autowired
    private CountyMasterService countyMasterService;


    //--------------------------------< Benefits Master > --------------------------------
    @Override
    public ResponseEntity<List<BenefitMasterResponse>> fetchBenefitsMasterData(String searchType, String searchKey) {
        return ResponseEntity.ok(benefitMasterService.fetchBenefitsMasterData(searchType, searchKey));
    }

    @Override
    public ResponseEntity<List<BenefitMasterResponse>> fetchBenefitsMasterByNameHaving(String code, String name) {
        return ResponseEntity.ok(benefitMasterService.fetchBenefitsMasterByCodeAndInterventionName(code, name));
    }

    @Override
    public ResponseEntity<List<BenefitMasterResponse>> fetchByCategoryCode(String searchKey) {
        return ResponseEntity.ok(benefitMasterService.fetchByCategoryCode(searchKey));
    }

    @Override
    public ResponseEntity<List<BenefitMasterResponse>> fetchByBenefitMasterAll() {
        return ResponseEntity.ok(benefitMasterService.fetchBenefitMasterAll());
    }

    @Override
    public ResponseEntity<List<BenefitMasterResponse>> fetchByBenefitMasterList(String interventionCodes) {
        return ResponseEntity.ok(benefitMasterService.fetchBenefitsMasterByList(interventionCodes));
    }

    @Override
    public ResponseEntity<CollectionModel<EntityModel<BenefitMasterResponse>>> fetchByBenefitMasterAllPage(String interventionCode, String interventionName, String shaCategory, String code, int page, int size, String sortBy, String direction) {
        return ResponseEntity.ok(benefitMasterService.fetchByBenefitMasterAllPage(interventionCode, interventionName, shaCategory, code, PageRequest.of(page, size, Sort.by(Helper.getSortDirection(direction), sortBy))));
    }

    @Override
    public ResponseEntity<BenefitMasterResponse> fetchBenefitById(Long id) {
        return benefitMasterService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<String> createBenefitMaster(@RequestBody List<BenefitMastersDTO> benefitsMasters) {
        String message = benefitMasterService.createBenefitMasters(benefitsMasters);
        return ResponseEntity.status(HttpStatus.CREATED).body(message);
    }

    @Override
    public ResponseEntity<String> updateBenefitMaster(@PathVariable Long id, @RequestBody BenefitMastersDTO benefitsMaster) {
        String message = benefitMasterService.updateBenefitMaster( id , benefitsMaster);
        return ResponseEntity.ok(message);
    }

  /**
   * @param file
   * @return
   */
  @Override
  public ResponseEntity<String> createBenefitMasterFromCSV(MultipartFile file) {
    benefitMasterService.processCSV(file);
    return ResponseEntity.ok("CSV processed and data saved successfully.");
  }

  //    @Override
  //    public ResponseEntity<String> deleteBenefitMaster(@PathVariable Long id) {
  //        String message = benefitMasterService.deleteBenefitMaster(id);
  //        return ResponseEntity.status(HttpStatus.OK).body(message);
  //    }

    // ---------------END-------------

    // Category Controllers
    @Override
    public ResponseEntity<List<CategoryMasterResponse>> fetchByCategoryName(String searchType, String searchKey) {
        return ResponseEntity.ok(categoryService.fetchCategory(searchType, searchKey));
    }

    @Override
    public ResponseEntity<List<CategoryMasterResponse>> fetchAllCategory() {
        return ResponseEntity.ok(categoryService.fetchAllCategory());
    }

    @Override
    public ResponseEntity<List<CountyMasterResponse>> fetchAllCounties() {
        return ResponseEntity.ok(countyMasterService.fetchAllCounties());
    }

    @Override
    public ResponseEntity<List<CountyOptionResponse>> fetchAllCountyOptions() {
        return ResponseEntity.ok(countyMasterService.fetchAllCountyOptions());
    }

    @Override
    public ResponseEntity<CollectionModel<EntityModel<CategoryMasterResponse>>> fetchAllPageableCategory(int page, int size, String sortBy, String direction) {
        return ResponseEntity.ok(categoryService.fetchAllPageableCategory(PageRequest.of(page, size, Sort.by(Helper.getSortDirection(direction), sortBy))));
    }

    // --------------------------------< PractitionerDetails > --------------------------------
    @Override
    public ResponseEntity<List<PractitionerDetailsResponse>> fetchAllPractitionerDetails() {
        return ResponseEntity.ok(practitionerDetailsService.fetchAllPractitionerDetails());
    }

    @Override
    public ResponseEntity<CollectionModel<EntityModel<PractitionerDetailsResponse>>> fetchAllPractitionerDetailsPage(int page, int size, String sortBy, String direction) {
        return ResponseEntity.ok(practitionerDetailsService.fetchAllPractitionerDetails(PageRequest.of(page, size, Sort.by(Helper.getSortDirection(direction), sortBy))));
    }

    @Override
    public ResponseEntity<List<PractitionerDetailsResponse>> fetchPractitionerDetailsByRegNo(String regNo) {
        return ResponseEntity.ok(practitionerDetailsService.fetchPractitionerDetailsByRegNo(regNo));
    }

    // --------------------------------< User County Assignment > --------------------------------
    
    @Override
    public ResponseEntity<List<UserCountyAssignmentResponse>> assignCountiesToUser(UserCountyAssignmentRequest request) {
        List<UserCountyAssignmentResponse> response = countyMasterService.assignCountiesToUser(request);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<UserCountySummaryResponse> getUserCountyAssignments(String username) {
        UserCountySummaryResponse response = countyMasterService.getUserCountyAssignments(username);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<CollectionModel<EntityModel<UserCountySummaryResponse>>> getAllUsersWithCountyAssignments(int page, int size, String sortBy, String direction) {
        return ResponseEntity.ok(countyMasterService.getAllUsersWithCountyAssignments(PageRequest.of(page, size, Sort.by(Helper.getSortDirection(direction), sortBy))));
    }

}
