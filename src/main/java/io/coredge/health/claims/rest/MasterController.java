package io.coredge.health.claims.rest;

import io.coredge.health.claims.annotations.RequiresPermission;
import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.enums.permission.ActionEnum;
import io.coredge.health.claims.enums.permission.ResourceEnum;
import io.coredge.health.claims.models.BenefitMastersDTO;
import io.coredge.health.claims.models.master.BenefitMasterResponse;
import io.coredge.health.claims.models.master.CategoryMasterResponse;
import io.coredge.health.claims.models.master.CountyMasterResponse;
import io.coredge.health.claims.models.master.PractitionerDetailsResponse;
import io.coredge.health.claims.models.master.CountyOptionResponse;
import io.coredge.health.claims.models.master.UserCountyAssignmentRequest;
import io.coredge.health.claims.models.master.UserCountyAssignmentResponse;
import io.coredge.health.claims.models.master.UserCountySummaryResponse;
import jakarta.validation.Valid;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@CrossOrigin
public interface MasterController {

    //--------------------------------< Benefits Master > --------------------------------
    @GetMapping(path = Constants.EndPoints.BENEFIT_MASTER_API_MAPPING, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<BenefitMasterResponse>> fetchBenefitsMasterData(@RequestParam String searchType, @RequestParam String searchKey);

    @GetMapping(path = Constants.EndPoints.BENEFIT_MASTER_API_NAME_HAVING_MAPPING, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<BenefitMasterResponse>> fetchBenefitsMasterByNameHaving(@RequestParam String code, @RequestParam String name);

    @GetMapping(path = Constants.EndPoints.BENEFIT_MASTER_CATEGORY_CODE_REQUEST_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<BenefitMasterResponse>> fetchByCategoryCode(@RequestParam String searchKey);

    @GetMapping(path = Constants.EndPoints.BENEFIT_MASTER_API_MAPPING_ALL, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<BenefitMasterResponse>> fetchByBenefitMasterAll();

    @GetMapping(path = Constants.EndPoints.BENEFIT_MASTER_API_MAPPING_ALL_LIST, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<BenefitMasterResponse>> fetchByBenefitMasterList(String interventionCodes);

    @GetMapping(path = Constants.EndPoints.BENEFIT_MASTER_API_MAPPING_ALL_PAGE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CollectionModel<EntityModel<BenefitMasterResponse>>> fetchByBenefitMasterAllPage(@RequestParam(required = false) String interventionCode, @RequestParam(required = false) String interventionName,
                                                                                                    @RequestParam(required = false) String shaCategory, @RequestParam(required = false) String code,@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size, @RequestParam(defaultValue = "id") String sortBy, @RequestParam(defaultValue = "ASC") String direction);

    @GetMapping(path=Constants.EndPoints.BENEFIT_MASTER_API_BY_ID ,produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<BenefitMasterResponse> fetchBenefitById(@PathVariable Long id);

    // create and update Interventions
    @PreAuthorize("hasRole('admin')")
    @RequiresPermission(resource = ResourceEnum.INTERVENTIONS, action = {ActionEnum.VIEW, ActionEnum.EDIT})
    @PostMapping(path = Constants.EndPoints.BENEFIT_MASTER_API_CREATE, consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> createBenefitMaster(@RequestBody List<BenefitMastersDTO> benefitsMasters);

    @PreAuthorize("hasRole('admin')")
    @RequiresPermission(resource = ResourceEnum.INTERVENTIONS, action = {ActionEnum.VIEW, ActionEnum.EDIT})
    @PutMapping(path = Constants.EndPoints.BENEFIT_MASTER_API_UPDATE +"/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> updateBenefitMaster(@PathVariable Long id, @RequestBody BenefitMastersDTO benefitsMaster);

    @PreAuthorize("hasRole('admin')")
    @RequiresPermission(resource = ResourceEnum.INTERVENTIONS, action = {ActionEnum.VIEW, ActionEnum.EDIT})
    @PostMapping(path = Constants.EndPoints.BENEFIT_MASTER_API_CSV, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<String> createBenefitMasterFromCSV(@RequestParam("file") MultipartFile file);

    //    @DeleteMapping(path = Constants.EndPoints.BENEFIT_MASTER_API_DELETE + "/{id}")
    //    ResponseEntity<String> deleteBenefitMaster(@PathVariable Long id);

    // Category Controllers
    @GetMapping(path = Constants.EndPoints.CATEGORY_MASTER_API_MAPPING, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<CategoryMasterResponse>> fetchByCategoryName(@RequestParam String searchType, @RequestParam String searchKey);

    @GetMapping(path = Constants.EndPoints.CATEGORY_MASTER_API_MAPPING_ALL, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<CategoryMasterResponse>> fetchAllCategory();

    @GetMapping(path = Constants.EndPoints.CATEGORY_MASTER_API_MAPPING_PAGE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CollectionModel<EntityModel<CategoryMasterResponse>>> fetchAllPageableCategory(int page, int size, String sortBy, String direction);

    // --------------------------------< PractitionerDetails > --------------------------------
    @GetMapping(path = Constants.EndPoints.PRACTITIONER_MASTER_API_MAPPING_ALL, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<PractitionerDetailsResponse>> fetchAllPractitionerDetails();

    @GetMapping(path = Constants.EndPoints.PRACTITIONER_MASTER_API_MAPPING_PAGE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CollectionModel<EntityModel<PractitionerDetailsResponse>>> fetchAllPractitionerDetailsPage(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size, @RequestParam(defaultValue = "id") String sortBy, @RequestParam(defaultValue = "ASC") String direction);

    @GetMapping(path = Constants.EndPoints.PRACTITIONER_MASTER_API_MAPPING_REG, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<PractitionerDetailsResponse>> fetchPractitionerDetailsByRegNo(String regNo);

    // County Controller
    @GetMapping(path = Constants.EndPoints.COUNTY_MASTER_API_MAPPING, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List <CountyMasterResponse>> fetchAllCounties();

    @GetMapping(path = Constants.EndPoints.COUNTY_MASTER_API_MAPPING_ALL, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<CountyOptionResponse>> fetchAllCountyOptions();

    // --------------------------------< User County Assignment > --------------------------------
    
    /**
     * Assign counties to a user.
     * Automatically deactivates existing assignments not in the request.
     * Supports assigning one or more counties.
     */
    @PreAuthorize("hasRole('admin')")
    @PostMapping(path = Constants.EndPoints.COUNTY_USER_ASSIGNMENT_ASSIGN, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<UserCountyAssignmentResponse>> assignCountiesToUser(@Valid @RequestBody UserCountyAssignmentRequest request);

    /**
     * Get all active counties assigned to a user
     */
    @GetMapping(path = Constants.EndPoints.COUNTY_USER_ASSIGNMENT_GET, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<UserCountySummaryResponse> getUserCountyAssignments(@PathVariable String username);

    /**
     * Get all users with their county assignments
     */
    @GetMapping(path = Constants.EndPoints.COUNTY_USER_ASSIGNMENT_ALL_PAGE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CollectionModel<EntityModel<UserCountySummaryResponse>>> getAllUsersWithCountyAssignments(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size, @RequestParam(defaultValue = "username") String sortBy, @RequestParam(defaultValue = "ASC") String direction);

}
