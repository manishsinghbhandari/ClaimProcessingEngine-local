package io.coredge.health.claims.service;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.db.entity.BenefitsMaster;
import io.coredge.health.claims.db.entity.benefits.DocumentType;
import io.coredge.health.claims.db.repositories.BenefitsMasterRepository;
import io.coredge.health.claims.db.repositories.benefits.DocumentTypeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import io.coredge.health.claims.enums.BenefitMasterSearchType;
import io.coredge.health.claims.enums.CategorySearchType;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.app.DataNotFoundException;
import io.coredge.health.claims.exception.app.DatabaseException;
import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.exception.base.DataBadRequestException;
import io.coredge.health.claims.exception.models.ErrorAttribute;
import io.coredge.health.claims.exception.models.ErrorCode;
import io.coredge.health.claims.helpers.Helper;
import io.coredge.health.claims.models.BenefitMastersDTO;
import io.coredge.health.claims.models.benefits.FundProjection;
import io.coredge.health.claims.models.master.BenefitMasterResponse;
import io.coredge.health.claims.models.master.CategoryMasterResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static io.coredge.health.claims.utils.FhirHelper.convertStringToDate;

@Slf4j
@Service
public class BenefitMasterService {

    @Autowired
    private BenefitsMasterRepository repository;

    @Autowired
    private DocumentTypeRepository documentTypeRepository;

    @Autowired
    private CategoryMasterService categoryMasterService;

    @Autowired
    private PagedResourcesAssembler<BenefitMasterResponse> pagedResourcesAssembler;

    private static BenefitMasterResponse getInterventionResponse(BenefitsMaster benefitsMasterEntity) {
        BenefitMasterResponse response = BenefitMasterResponse.builder().build();
        BeanUtils.copyProperties(benefitsMasterEntity, response); // Copy properties
        return response;
    }

    public Optional<List<BenefitsMaster>> findAll() {
        return Optional.of(repository.findAll());
    }

    public Optional<List<BenefitsMaster>> findAllList(List<String> interventionCodes) {
        return repository.findByInterventionCodeIn(interventionCodes);
    }

    public List<BenefitMasterResponse> fetchBenefitsMasterByList(String interventionCodes) {
        // Validate input
        if (interventionCodes == null || interventionCodes.isBlank()) {
            throw new BadRequestException();
        }
        // Split the comma-separated string into a list, removing any extra whitespace
        List<String> interventionCodeList = Arrays.stream(interventionCodes.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .toList();

        return findAllList(interventionCodeList)
                .filter(list -> !list.isEmpty())
                .map(
                        list -> list.stream() // If data is present, stream over the list
                                .map(BenefitMasterService::getInterventionResponse) // Process each item
                                .collect(Collectors.toList())) // Collect the results into a list
                .orElse(Collections.emptyList()); // If no data is present, return an empty list or throw
    }

    public Optional<Page<BenefitsMaster>> findAll(Pageable pageable) {
        return Optional.of(repository.findAll(pageable));
    }

    public Optional<BenefitMasterResponse> findByUid(Long id) {
        return repository.findById(id)
                .map(benefitsMaster -> {
                    BenefitMasterResponse response = BenefitMasterResponse.builder().build();
                    BeanUtils.copyProperties(benefitsMaster, response);
                    return response;
                });
    }

    public Optional<BenefitMasterResponse> findById(Long id) {
        return repository.findById(id)
                .map(benefitsMaster -> {
                    BenefitMasterResponse response = BenefitMasterResponse.builder().build();
                    BeanUtils.copyProperties(benefitsMaster, response);
                    return response;
                });
    }

    public BenefitsMaster save(BenefitsMaster benefitsMaster) {
        return repository.save(benefitsMaster);
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    public List<BenefitMasterResponse> fetchBenefitsMasterData(String searchType, String searchKey) {
        Optional<List<BenefitsMaster>> response;
        if (BenefitMasterSearchType.CATEGORY.getValue().equals(searchType)) {
            response = getByShaCategory(searchKey);
        } else if (BenefitMasterSearchType.CATEGORY_CODE.getValue().equals(searchType)) {
            response = getByShaCategoryCode(searchKey);
        } else if (BenefitMasterSearchType.SUB_CATEGORY.getValue().equals(searchType)) {
            response = getBySubCategoryBenefitsPackage(searchKey);
        } else if (BenefitMasterSearchType.INTERVENTION.getValue().equals(searchType)) {
            response = getByInterventionCode(searchKey);
        } else {
            ErrorAttribute attribute = ErrorAttribute.builder().key(searchType).value(searchKey).build();
            throw new BadRequestException(attribute);
        }

        return response
                .filter(list -> !list.isEmpty())
                .map(
                        list -> list.stream() // If data is present, stream over the list
                                .map(BenefitMasterService::getInterventionResponse) // Process each item
                                .collect(Collectors.toList())) // Collect the results into a list
                .orElseThrow(
                        DataNotFoundException::new); // If no data is present, return an empty list or throw
    }

    public List<BenefitMasterResponse> fetchBenefitsMaster(String searchKey) {
        return getByInterventionCode(searchKey)
                .filter(list -> !list.isEmpty())
                .map(list -> list.stream() // If data is present, stream over the list
                        .map(BenefitMasterService::getInterventionResponse) // Process each item
                        .collect(Collectors.toList())) // Collect the results into a list
                .orElse(Collections.emptyList()); // If no data is present, return an empty list or throw
    }

    private Optional<List<BenefitsMaster>> getByInterventionCodeIn(List<String> interventionCodes) {
        return repository.findByInterventionCodeIn(interventionCodes);
    }

    private Optional<List<BenefitsMaster>> getByInterventionCodeNotIn(List<String> interventionCodes) {
        return repository.findByInterventionCodeNotIn(interventionCodes);
    }

    public boolean existsByInterventionCode(String interventionCode) {
        return repository.existsByInterventionCode(interventionCode);
    }

    public List<BenefitMasterResponse> fetchBenefitsMasterByInterventionName(String searchValue) {
        return getByInterventionNameHaving(searchValue)
                .filter(list -> !list.isEmpty())
                .map(
                        list -> list.stream() // If data is present, stream over the list
                                .map(BenefitMasterService::getInterventionResponse) // Process each item
                                .collect(Collectors.toList())) // Collect the results into a list
                .orElseThrow(DataNotFoundException::new);
    }

    public BenefitsMaster fetchByInterventionCode(String searchKey) {
        BenefitsMaster master = new BenefitsMaster();
        try {
            Optional<List<BenefitsMaster>> response = repository.findByInterventionCode(searchKey);
            if (response.isPresent() && !response.get().isEmpty()) {
                return response.get().getFirst();
            }
            return master;
        } catch (Exception e) {
            throw new DatabaseException();
        }
    }

    private Optional<List<BenefitsMaster>> getByInterventionCode(String searchKey) {
        try {
            return repository.findByInterventionCode(searchKey);
        } catch (Exception e) {
            throw new DatabaseException();
        }
    }

    private Optional<List<BenefitsMaster>> getBySubCategoryBenefitsPackage(String searchKey) {
        try {
            return repository.findBySubCategoryBenefitsPackage(searchKey);
        } catch (Exception e) {
            throw new DatabaseException();
        }
    }

    private Optional<List<BenefitsMaster>> getByShaCategory(String searchKey) {
        try {
            return repository.findByShaCategory(searchKey);
        } catch (Exception e) {
            throw new DatabaseException();
        }
    }

    private Optional<List<BenefitsMaster>> getByShaCategoryCode(String searchKey) {
        try {
            List<CategoryMasterResponse> categoryMasterResponses = categoryMasterService
                    .fetchCategory(CategorySearchType.code.getValue(), searchKey);
            return repository
                    .findByShaCategoryContainingIgnoreCase(categoryMasterResponses.getFirst().getCategoryName());
        } catch (Exception e) {
            throw new DatabaseException();
        }
    }

    private Optional<List<BenefitsMaster>> getByInterventionNameHaving(String searchKey) {
        try {
            return repository.findByInterventionNameContainingIgnoreCase(searchKey);
        } catch (Exception e) {
            throw new DatabaseException();
        }
    }

    public List<BenefitMasterResponse> fetchBenefitsMasterByCodeAndInterventionName(String code, String searchKey) {
        return getByCodeAndInterventionNameHaving(code, searchKey)
                .filter(list -> !list.isEmpty())
                .map(
                        list -> list.stream() // If data is present, stream over the list
                                .map(BenefitMasterService::getInterventionResponse) // Process each item
                                .collect(Collectors.toList())) // Collect the results into a list
                .orElseThrow(DataNotFoundException::new);
    }

    private Optional<List<BenefitsMaster>> getByCodeAndInterventionNameHaving(String code, String searchKey) {
        try {
            return repository.findByCodeAndInterventionNameContainingIgnoreCase(code, searchKey);
        } catch (Exception e) {
            throw new DatabaseException();
        }
    }

    public List<BenefitMasterResponse> fetchByCategoryCode(String searchValue) {
        try {
            return fetchBenefitMasterByCategoryCode(searchValue).filter(list -> !list.isEmpty())
                    .map(list -> list.stream().map(BenefitMasterService::getInterventionResponse)
                            .collect(Collectors.toList()))
                    .orElseThrow(DataNotFoundException::new);
        } catch (Exception e) {
            throw new DatabaseException();
        }
    }

    public Optional<List<BenefitsMaster>> fetchBenefitMasterByCategoryCode(String searchValue) {
        try {
            return repository.findByCode(searchValue);
        } catch (Exception e) {
            throw new DatabaseException();
        }
    }

    @Cacheable(value = "benefitMaster")
    public List<BenefitMasterResponse> fetchBenefitMasterAll() {
        return findAll()
                .filter(list -> !list.isEmpty())
                .map(list -> list.stream()
                        .map(BenefitMasterService::getInterventionResponse)
                        .collect(Collectors.toList()))
                .orElseThrow(DataNotFoundException::new);
    }

    public PagedModel<EntityModel<BenefitMasterResponse>> fetchByBenefitMasterAllPage(String interventionCode,
            String interventionName, String shaCategory, String code, Pageable pageable) {
        Optional<Page<BenefitsMaster>> allPractitioners = findAllBenefitsFilter(interventionCode,
                Helper.decodeValue(shaCategory), code, Helper.decodeValue(interventionName), pageable);
        Page<BenefitMasterResponse> practitionerDetailsResponses = allPractitioners
                .filter(Page::hasContent)
                .map(page -> page.map(BenefitMasterService::getInterventionResponse))
                .orElseThrow(DataNotFoundException::new);
        return pagedResourcesAssembler.toModel(practitionerDetailsResponses);
    }

    private Optional<Page<BenefitsMaster>> findAllBenefitsFilter(String interventionCode, String shaCategory,
            String code, String interventionName, Pageable pageable) {
        return repository.findBenefitsByAllOptionalParams(interventionCode, shaCategory, code, interventionName,
                pageable);
    }

    public String createBenefitMasters(List<BenefitMastersDTO> benefitsMastersDTOs) {
        List<BenefitsMaster> benefitsMasters = benefitsMastersDTOs.stream()
                .map(this::convertToEntity)
                .toList();
        repository.saveAll(benefitsMasters);
        return "Benefit Data successfully saved.";
    }

    public String updateBenefitMaster(Long id, BenefitMastersDTO benefitsMasterDTO) {
        if (!id.equals(benefitsMasterDTO.getId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "The ID in the path and the ID in the request body do not match.");
        }
        BenefitsMaster existingEntity = repository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Benefit Data not found for the provided ID"));
        for (Field field : BenefitMastersDTO.class.getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object valueNew = field.get(benefitsMasterDTO);
                if (valueNew != null) {
                    Field entityField = BenefitsMaster.class.getDeclaredField(field.getName());
                    entityField.setAccessible(true);
                    entityField.set(existingEntity, valueNew);
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException("Error updating Benefit Data", e);
            }
        }
        repository.save(existingEntity);
        return "Benefit Data successfully updated.";
    }

    public String deleteBenefitMaster(Long id) {
        repository.deleteById(id);
        return "Benefit Data successfully deleted.";
    }

    private BenefitsMaster convertToEntity(BenefitMastersDTO dto) {
        BenefitsMaster entity = new BenefitsMaster();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    public void processCSV(MultipartFile file) {
        List<BenefitMastersDTO> benefitMasters = new ArrayList<>();
        Map<Integer, String> validationErrors = new LinkedHashMap<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            CSVParser csvParser = CSVParser.parse(reader, CSVFormat.DEFAULT.withHeader().withIgnoreEmptyLines());
            for (CSVRecord record : csvParser) {
                try {
                    BenefitMastersDTO dto = mapRecordToDTO(record);
                    benefitMasters.add(dto);
                } catch (DataBadRequestException e) {
                    validationErrors.put((int) record.getRecordNumber(), e.getMessage());
                }
            }

            if (!validationErrors.isEmpty()) {
                String aggregatedErrors = validationErrors.entrySet().stream()
                        .map(entry -> "Record number " + entry.getKey() + " " + entry.getValue())
                        .collect(Collectors.joining("\n"));

                throw new DataBadRequestException(ErrorCode.INVALID_DATA,
                        "Validation errors found: " + aggregatedErrors);
            }

            // Save and convert DTOs to entities
            List<BenefitsMaster> entities = benefitMasters.stream()
                    .map(this::convertToEntity)
                    .collect(Collectors.toList());
            repository.saveAll(entities);
        } catch (IOException e) {
            throw new RuntimeException("Error processing CSV file", e);
        }
    }

    private BenefitMastersDTO mapRecordToDTO(CSVRecord record) {
        BenefitMastersDTO dto = new BenefitMastersDTO();
        Set<String> requiredFields = getRequiredFields();

        List<String> missingFields = new ArrayList<>();
        List<String> missingOrNullValues = new ArrayList<>();

        for (String fieldName : record.toMap().keySet()) {
            if (!record.isSet(fieldName)) {
                missingFields.add(fieldName);
                continue;
            }
            String value = record.get(fieldName);
            if (requiredFields.contains(fieldName) && (value == null || value.trim().isEmpty())) {
                missingOrNullValues.add(fieldName);
                continue;
            }
            try {
                Field field = BenefitMastersDTO.class.getDeclaredField(fieldName);
                field.setAccessible(true);

                if (field.getType().equals(Boolean.class) || field.getType().equals(boolean.class)) {
                    field.set(dto, Boolean.parseBoolean(value));
                } else if (field.getType().equals(List.class)) {
                    field.set(dto, Arrays.asList(value.split(";")));
                } else if (field.getType().equals(Date.class)) {
                    field.set(dto, convertStringToDate(value));
                } else {
                    field.set(dto, value);
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException("Error mapping field '" + fieldName + "': " + e.getMessage());
            }
        }

        for (String requiredField : requiredFields) {
            if (!record.toMap().containsKey(requiredField)) {
                missingFields.add(requiredField);
            }
        }

        if (!missingFields.isEmpty() || !missingOrNullValues.isEmpty()) {
            String errorMessage = String.format(
                    " missing fields: %s and missing or null values for fields: %s",
                    String.join(", ", missingFields),
                    String.join(", ", missingOrNullValues));
            throw new DataBadRequestException(
                    ErrorCode.INVALID_DATA,
                    "Validation errors found: " + errorMessage);
        }
        return dto;
    }

    private Set<String> getRequiredFields() {
        return Set.of(
                "interventionCode", "shaCategory", "code", "accessCode",
                "subCategoryBenefitsPackage", "interventionName", "categoryHealthFacility",
                "interventionQuantityYear", "tariffs", "limitIndividual", "phc",
                "shif", "eccif", "automateManual", "status");
    }

    public boolean isPhcClaim(String interventionCode) {
        return repository.existsByInterventionCodeAndProviderPaymentMechanism(interventionCode,
                Constants.GLOBAL_CAPITATION_CAPITATION);
    }

    public boolean isAiCheckEnabled(String interventionCode) {
        return repository.existsByInterventionCodeAndEfwaCheckEnabledIsTrue(interventionCode);
    }

    public List<FundProjection> getFundsByIntervention(List<String> interventionCode) {
        return repository.findFundByInterventionCodes(interventionCode);
    }

    public Set<DocumentType> fetchDocumentTypesByBenefitIdAndUse(Long benefitsMasterId) {
       return new HashSet<>(documentTypeRepository.findByBenefitMasterIdAndUse(benefitsMasterId));
    }

}
