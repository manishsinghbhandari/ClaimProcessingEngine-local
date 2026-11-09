package io.coredge.health.claims.service;

import ca.uhn.fhir.parser.IParser;
import io.coredge.health.claims.db.entity.Diagnosis;
import io.coredge.health.claims.db.repositories.DiagnosisRepository;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.app.DataNotFoundException;
import io.coredge.health.claims.exception.models.ErrorAttribute;
import io.coredge.health.claims.helpers.DiagnosisHelper;
import io.coredge.health.claims.models.DiagnosisResponse;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class DiagnosisService {

    @Autowired
    private DiagnosisRepository diagnosisRepository;
    @Autowired
    private IParser fhirParser;


    public List<DiagnosisResponse> fetchDiagnosis(String code) {
        if (!StringUtils.hasText(code)) {
            ErrorAttribute attribute = ErrorAttribute.builder().key("DiagnosisCode").value("Diagnosis code is null or empty").build();
            throw new BadRequestException(attribute);
        }

        Optional<List<Diagnosis>> diagnosisList = diagnosisRepository.findByCode(code);
        if (diagnosisList.isEmpty()) {
            throw new DataNotFoundException("No diagnosis found for code: " + code);
        }

        return diagnosisList.map(list -> list.stream()
                .map(DiagnosisHelper::getDiagnosisResponse)
                .collect(Collectors.toList()))
                .orElseThrow(() -> new DataNotFoundException("No diagnosis found for code: " + code));
    }

    public List<DiagnosisResponse> fetchDiagnosisByClaimID(String claimId) {
        if (!StringUtils.hasText(claimId)) {
            ErrorAttribute attribute = ErrorAttribute.builder()
                    .key("ClaimID").value("Claim id is null or empty").build();
            throw new BadRequestException(attribute);
        }

        Optional<List<Diagnosis>> diagnosisList = diagnosisRepository.findByClaimId(claimId);
        if (diagnosisList.isEmpty()) {
            throw new DataNotFoundException("No diagnosis found for claim id: " + claimId);
        }
        return diagnosisList.map(list -> list.stream()
                .map(DiagnosisHelper::getDiagnosisResponse)
                .collect(Collectors.toList()))
                .orElseThrow(() -> new DataNotFoundException("No diagnosis found for claim id: " + claimId));
    }

    public ResponseEntity<String> saveDiagnosis(String claimStr) {
        if (!StringUtils.hasText(claimStr)) {
            throw new BadRequestException(ErrorAttribute.builder()
                    .key("ClaimBundle").value("Claim bundle is null or empty").build());
        }
        try {
            Claim claim = DiagnosisHelper.getClaimObj(fhirParser.parseResource(Bundle.class, claimStr));
            List<Diagnosis> diagnoses = Optional.ofNullable(claim).filter(Claim::hasDiagnosis).map(DiagnosisHelper::getDiagnosisArr).orElse(Collections.emptyList());

            if (diagnoses.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(claim == null ? "No Claim obj found in bundle." : "No valid diagnoses found to save.");
            }

            diagnosisRepository.saveAll(diagnoses);
            return ResponseEntity.status(HttpStatus.CREATED).body("Data has been saved.");
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body("Invalid input JSON: " + ex.getMessage());
        }
    }

}
