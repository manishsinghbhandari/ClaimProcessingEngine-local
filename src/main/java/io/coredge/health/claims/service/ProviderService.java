package io.coredge.health.claims.service;

import io.coredge.health.claims.db.entity.Provider;
import io.coredge.health.claims.db.repositories.ProviderRepository;
import io.coredge.health.claims.db.repositories.provider.ProviderSpec;
import io.coredge.health.claims.enums.ProviderSearchType;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.app.DataNotFoundException;
import io.coredge.health.claims.exception.app.DatabaseException;
import io.coredge.health.claims.exception.models.ErrorAttribute;
import io.coredge.health.claims.models.master.ProviderResponse;
import io.coredge.health.claims.models.master.SuggestionResponse;
import io.coredge.health.claims.service.cpp.CppService;
import io.coredge.health.claims.utils.FhirHelper;
import jakarta.transaction.Transactional;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.apache.jena.sparql.engine.optimizer.reorder.ReorderTransformationSubstitution.log;

@Service
public class ProviderService {

    @Autowired
    private ProviderRepository repository;
    @Autowired
    private CppService cppService;

    @Autowired
    private PagedResourcesAssembler<ProviderResponse> pagedResourcesAssembler;

    private static ProviderResponse getResponse(Provider facilityMaster) {
        ProviderResponse response = ProviderResponse.builder().build();
        BeanUtils.copyProperties(facilityMaster, response); // Copy properties
        return response;
    }

    /**
     * @param facilityMaster
     * @return
     */
    private static ProviderResponse getFacilityMasterResponse(Provider facilityMaster) {
        ProviderResponse response = ProviderResponse.builder().build();
        BeanUtils.copyProperties(facilityMaster, response); // Copy properties
        return response;
    }

    /**
     * @param facilityMaster
     * @return
     */
    public static SuggestionResponse getSuggestionResponse(Provider facilityMaster) {
        return  SuggestionResponse.builder().code(facilityMaster.getMflCode()).name(facilityMaster.getName()).build();
    }

    public Optional<Page<Provider>> fetchAllProviderDetails(String name, String fidCode, String level, String county, String regNo, Pageable pageable) {
        Page<Provider> page = repository.findAll(ProviderSpec.filter(name, fidCode, level, county, regNo), pageable);
        return Optional.of(page);
    }

    @Cacheable("allProviders")
    public Optional<List<Provider>> findAll() {
        return Optional.of(repository.findAll());
    }

    @Cacheable("provider")
    public Optional<Provider> findById(Long id) {
        return repository.findById(id);
    }

    public Provider save(Provider facilityMaster) {
        return repository.save(facilityMaster);
    }

//    public void deleteById(Long id) {
//        repository.deleteById(id);
//    }

    public List<ProviderResponse> fetchProviderDetails(ProviderSearchType searchKey, String searchValue) {

        Optional<List<Provider>> response;
        if (ProviderSearchType.ID.equals(searchKey)) {
            response = getById(searchValue);
        } else if (ProviderSearchType.FID_CODE.equals(searchKey)) {
            response = getByFidCode(searchValue);
        } else if (ProviderSearchType.REG_NO.equals(searchKey)) {
            response = getByRegNo(searchValue);
        } else {
            ErrorAttribute attribute = ErrorAttribute.builder().value(searchKey.name()).key(searchValue).build();
            throw new BadRequestException(attribute);
        }
        return mapperWithOptional(response);

    }

    public PagedModel<EntityModel<ProviderResponse>> fetchAllProviderDetailsByLevelPage(String level, Pageable pageable) {
        Optional<Page<Provider>> allPractitioners = searchByName(level, pageable);
        Page<ProviderResponse> facilityMasterResponse = allPractitioners
                .filter(Page::hasContent)
                .map(page -> page.map(ProviderService::getFacilityMasterResponse))
                .orElseThrow(DataNotFoundException::new);
        return pagedResourcesAssembler.toModel(facilityMasterResponse);
    }
    public PagedModel<EntityModel<ProviderResponse>> fetchAllProviderDetailsPage(String name, String fidCode, String level, String county, String regNo, Pageable pageable) {
        Optional<Page<Provider>> allPractitioners = fetchAllProviderDetails(name, fidCode, level, county, regNo,pageable);
        Page<ProviderResponse> facilityMasterResponse = allPractitioners
                .filter(Page::hasContent)
                .map(page -> page.map(ProviderService::getFacilityMasterResponse))
                .orElseThrow(DataNotFoundException::new);
        return pagedResourcesAssembler.toModel(facilityMasterResponse);
    }

    public List<ProviderResponse> getProviderDetailsList() {
        Optional<List<Provider>> facilityMasterList = findAll();
        return facilityMasterList.filter(list -> !list.isEmpty()).map(list -> list.stream() // If data is present, stream over the list
                        .map(ProviderService::getFacilityMasterResponse) // Process each item
                        .collect(Collectors.toList())) // Collect the results into a list
                .orElseThrow(DataNotFoundException::new);// If no data is present, return an empty list or throw
    }

    public List<ProviderResponse> getProviderByFid(String fidCode) {
        Optional<List<Provider>> providerDetailsDB = getByFidCode(fidCode);
        return providerDetailsDB.filter(list -> !list.isEmpty()).map(list -> list.stream() // If data is present, stream over the list
                        .map(ProviderService::getResponse) // Process each item
                        .collect(Collectors.toList())) // Collect the results into a list
                .orElseThrow(DataNotFoundException::new);
    }

    private List<ProviderResponse> mapperWithOptional(Optional<List<Provider>> facilityMasters) {
        return facilityMasters.filter(list -> !list.isEmpty()).map(list -> list.stream() // If data is present, stream over the list
                        .map(ProviderService::getResponse) // Process each item
                        .collect(Collectors.toList())) // Collect the results into a list
                .orElseThrow(DataNotFoundException::new);
    }

    private Page<ProviderResponse> mapperWithPageableList(Page<Provider> facilityMasters) {
        List<ProviderResponse> responses = facilityMasters.getContent().stream().map(ProviderService::getResponse) // Process each item
                .collect(Collectors.toList());
        return new PageImpl<>(responses, facilityMasters.getPageable(), facilityMasters.getTotalElements());
    }

    private Optional<List<Provider>> getByFidCode(String searchKey) {
        try {
            return repository.findByFidCode(searchKey);
        } catch (Exception e) {
            throw new DatabaseException();
        }
    }

    private Optional<List<Provider>> getById(String searchKey) {
        try {
            return repository.findById(Long.valueOf(searchKey)).map(List::of).or(() -> Optional.of(List.of()));
        } catch (Exception e) {
            throw new DatabaseException();
        }
    }

    private Optional<List<Provider>> getByRegNo(String searchKey) {
        try {
            return repository.findByRegNo(searchKey);
        } catch (Exception e) {
            throw new DatabaseException();
        }
    }

    private Optional<Page<Provider>> searchByName(String searchKey, Pageable pageable) {
        try {
            return repository.findByLevelIgnoreCase(searchKey.toLowerCase(), pageable);
        } catch (Exception e) {
            throw new DatabaseException();
        }
    }

    @Cacheable(value = "facilitySearchCache", key = "#searchValue")
    public List<SuggestionResponse> searchAllFacility(String searchValue) {
        Optional<Page<Provider>> response = searchByName(searchValue);
        Page<SuggestionResponse> result = response.filter(Page::hasContent)
                .map(page -> page.map(provider ->
                        SuggestionResponse.builder()
                                .code(provider.getId().toString())
                                .name(provider.getName())
                                .details(provider.getCounty() != null ? provider.getCounty() : "")
                                .build()))
                .orElseThrow(DataNotFoundException::new);

        return result.getContent();
    }

    private String addCounty(String name, String county) {
        if (county != null) {
            return name + " (" + county + ")";
        } else {
            return name;
        }
    }

    private Optional<Page<Provider>> searchByName(String searchKey) {
        try {
            int page = 0;
            int size = 10;
            String sortBy = "name";
            return repository.findDistinctByNameContainingIgnoreCase(searchKey, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, sortBy)));
        } catch (Exception e) {
            throw new DatabaseException();
        }
    }

    public ProviderResponse findById(String id) {
        Optional<List<Provider>> optionalProviders;
        if (id.contains("FID")) {
            optionalProviders = repository.findByFidCode(id);
        } else {
            optionalProviders = repository.findByMflCode(id);
        }
        if (optionalProviders.isPresent() && !optionalProviders.get().isEmpty()) {
            return getResponse(optionalProviders.get().getFirst());
        }
        throw new DataNotFoundException("No provider data found for given ID: " + id);
    }

    @Transactional
    public Provider saveToDatabase(Provider provider, Optional<Provider> response) {
        log.info("Processing Provider FidCode: {}", provider.getFidCode());
        Optional<List<Provider>> providerList = repository.findByFidCode(provider.getFidCode());
        if (providerList.isPresent() && !providerList.get().isEmpty()) {
            log.info("Already found in DB , Updating Info,  Provider FR-CODE: {}", provider.getFidCode());
            Provider providerDB = providerList.get().getFirst();
            Long id = providerDB.getId();
            boolean isEfwaCheckEnabled  = providerDB.getEfwaCheckEnabled();
            boolean isPreauthEfwaEnabled = Boolean.TRUE.equals(providerDB.getPreauthEfwaEnabled());
            BeanUtils.copyProperties(provider, providerDB);
            // Copy only non-null properties from provider to providerDB
            providerDB.setId(id);
            providerDB.setEfwaCheckEnabled(isEfwaCheckEnabled);
            providerDB.setPreauthEfwaEnabled(isPreauthEfwaEnabled);
            return repository.save(providerDB);
        } else {
            log.info("Pushing Provider to CPP,  FID-CODE: {}", provider.getFidCode());
            cppService.pushToCpp(provider);
            log.info("Inserting New Provider in DB Provider FID-CODE: {}", provider.getFidCode());
            return repository.save(provider);
        }
    }

}
