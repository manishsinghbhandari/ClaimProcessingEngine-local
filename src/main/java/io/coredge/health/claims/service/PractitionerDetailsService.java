package io.coredge.health.claims.service;

import io.coredge.health.claims.db.entity.PractitionerDetails;
import io.coredge.health.claims.db.repositories.PractitionerDetailsRepository;
import io.coredge.health.claims.exception.app.DataNotFoundException;
import io.coredge.health.claims.exception.app.DatabaseException;
import io.coredge.health.claims.models.master.PractitionerDetailsResponse;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;


@Slf4j
@Service
public class PractitionerDetailsService {

    @Autowired
    private PractitionerDetailsRepository practitionerDetailsRepository;

    @Autowired
    private PagedResourcesAssembler<PractitionerDetailsResponse> pagedResourcesAssembler;


    private static PractitionerDetailsResponse getPractitionerDetailsResponse(PractitionerDetails practitionerDetailsEntity) {
        PractitionerDetailsResponse practitionerDetailsResponse = PractitionerDetailsResponse.builder().build();
        BeanUtils.copyProperties(practitionerDetailsEntity, practitionerDetailsResponse);
        return practitionerDetailsResponse;
    }

    public List<PractitionerDetailsResponse> fetchPractitionerDetailsByRegNo(String regNo) {
        return getPractitionerByRegNo(regNo)
                .filter(list -> !list.isEmpty())
                .map(list -> list.stream() // If data is present, stream over the list
                                        .map(PractitionerDetailsService::getPractitionerDetailsResponse) // Process each item
                                        .collect(Collectors.toList())) // Collect the results into a list
                .orElseThrow(DataNotFoundException::new);
    }

    public List<PractitionerDetailsResponse> fetchAllPractitionerDetails() {
        return getAllPractitioners()
                .filter(list -> !list.isEmpty())
                .map(list -> list.stream() // If data is present, stream over the list
                        .map(PractitionerDetailsService::getPractitionerDetailsResponse) // Process each item
                        .collect(Collectors.toList())) // Collect the results into a list
                .orElseThrow(DataNotFoundException::new);
    }

    public PagedModel<EntityModel<PractitionerDetailsResponse>> fetchAllPractitionerDetails(Pageable pageable) {
        Optional<Page<PractitionerDetails>> allPractitioners = getAllPractitioners(pageable);
        Page<PractitionerDetailsResponse> practitionerDetailsResponses = allPractitioners
                .filter(Page::hasContent)
                .map(page -> page.map(PractitionerDetailsService::getPractitionerDetailsResponse))
                .orElseThrow(DataNotFoundException::new);
        return pagedResourcesAssembler.toModel(practitionerDetailsResponses);
    }

    private PractitionerDetails savePractitioner(PractitionerDetails practitioner) {
        return practitionerDetailsRepository.save(practitioner);
    }

    private Optional<List<PractitionerDetails>> getPractitionerByRegNo(String regNo) {
        return practitionerDetailsRepository.findByRegNo(regNo);
    }

    private Optional<List<PractitionerDetails>> getAllPractitioners() {
        return Optional.of(practitionerDetailsRepository.findAll());
    }

    private Optional<Page<PractitionerDetails>> getAllPractitioners(Pageable pageable) {
        return Optional.of(practitionerDetailsRepository.findAll(pageable));
    }

    private void deletePractitioner(Long id) {
        practitionerDetailsRepository.deleteById(id);
    }

    private Optional<List<PractitionerDetails>>findByRegNo(String regNo) {
        try {
            return practitionerDetailsRepository.findByRegNo(regNo);
        } catch (Exception e) {
            throw new DatabaseException(e.getMessage().toUpperCase());
        }
    }

    private boolean existsByRegNo(String regNo) {
        try {
            return practitionerDetailsRepository.existsByRegNo(regNo).orElse(false);
        }catch (Exception e) {
            throw new DatabaseException(e.getMessage().toUpperCase());
        }
    }

    @Transactional
    public PractitionerDetails saveToDatabase(PractitionerDetails practitioner) {

        log.info("Processing Practitioner RegNo: {}", practitioner.getRegNo());
        if (existsByRegNo(practitioner.getRegNo())) {
            // Update the field that doesn't have the value
            log.info("Already found in DB ,Updating Info,  Practitioner RegNo: {}", practitioner.getRegNo());
            PractitionerDetails practitionerInDB = Objects.requireNonNull(findByRegNo(practitioner.getRegNo().trim()).orElse(null)).getFirst();
            for (Field field : PractitionerDetails.class.getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    Object valueInDB = field.get(practitionerInDB);
                    Object valueNew = field.get(practitioner);

                    if (valueInDB == null && valueNew != null) {
                        field.set(practitionerInDB, valueNew);
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Error updating member fields", e);
                }
            }
            return practitionerDetailsRepository.save(practitionerInDB);
        } else {
            // Save to db if Patient not in DB
            return practitionerDetailsRepository.save(practitioner);
        }
    }
    public List<String> findAllPractitionerRegNo(){
        return practitionerDetailsRepository.findAllPractitionerRegNo();
    }


}
