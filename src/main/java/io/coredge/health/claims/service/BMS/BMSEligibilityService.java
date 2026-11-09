package io.coredge.health.claims.service.BMS;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.dto.eligibility.EligibilityResponseDTO;
import io.coredge.health.claims.dto.eligibility.EmployerDetailsDTO;
import io.coredge.health.claims.dto.eligibility.EligibilityBenefitDTO;
import io.coredge.health.claims.dto.eligibility.SchemeBenefitDTO;
import io.coredge.health.claims.db.entity.Member;
import io.coredge.health.claims.db.entity.BMS.*;
import io.coredge.health.claims.db.repositories.MemberRepository;
import io.coredge.health.claims.db.repositories.BMS.*;

import io.coredge.health.claims.exception.app.EligibilityFileProcessingException;
import io.coredge.health.claims.exception.app.EmptyFileException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for processing BMS eligibility data. Handles member creation, scheme
 * member assignment, and benefit limit setup based on eligibility input.
 * Author: Reinhard Odhiambo
 */
@Slf4j
@Service
@AllArgsConstructor
public class BMSEligibilityService {

    // Repository dependencies for accessing and persisting data
    private final MemberRepository patientRepository;
    private final SchemeMemberRepository schemeMemberRepository;
    private final SchemeDetailRepository schemeDetailRepository;
    private final BenefitRepository benefitRepository;
    private final SchemeMemberLimitRepository schemeMemberLimitRepository;
    private final JobGroupRepository jobGroupRepository;
    private final MemberRelationRepository memberRelationRepository;
    private final SchemeBenefitLimitRepository schemeBenefitLimitRepository;

    /**
     * Processes a single eligibility DTO: - Creates or updates a Member entity.
     * - Creates or updates SchemeMember and their limits for each
     * scheme/benefit.
     *
     * @param dto The eligibility data to process.
     */
    public List<SchemeMemberLimit> processEligibility(EligibilityResponseDTO dto) {
        log.info("Starting eligibility processing for: {}", dto.getFullName());

        // Step 1: Check if Member exists
        Member patient = getPatient(dto);
        log.info("Using patient: {}", patient.getId());

        List<SchemeMemberLimit> schemeMembers = new ArrayList<>();

        for (EligibilityBenefitDTO benefitDTO : dto.getBenefits()) {
            String schemeCode = benefitDTO.getSchemeCode();
            log.info("Processing scheme: {}", schemeCode);

            SchemeDetail schemeDetail = schemeDetailRepository.findBySystemCode(schemeCode).orElse(null);
            if (schemeDetail == null) {
                log.warn("SchemeDetail not found for code: {}", schemeCode);
                continue;
            }

            // Fetch/create SchemeMember
            SchemeMember schemeMember = getSchemeMember(dto, patient, schemeDetail);
            log.info("Using SchemeMember: {}", schemeMember.getId());

            // Step 3: Process benefits in bulk
            List<SchemeBenefitDTO> schemeBenefits = benefitDTO.getSchemeBenefits();
            List<String> benefitCodes = schemeBenefits.stream()
                    .map(SchemeBenefitDTO::getBenefitCode)
                    .toList();

            Map<String, Benefit> benefitMap = benefitRepository.findByCodeIn(benefitCodes).stream()
                    .collect(Collectors.toMap(Benefit::getCode, b -> b));

            List<Benefit> benefits = new ArrayList<>(benefitMap.values());

            Set<String> existingBenefitCodes = schemeMemberLimitRepository
                    .findBySchemeMemberAndBenefitInAndSchemeDetail(schemeMember, benefits, schemeDetail).stream()
                    .map(l -> l.getBenefit().getCode())
                    .collect(Collectors.toSet());

            Map<String, SchemeBenefitLimit> benefitLimitMap = schemeBenefitLimitRepository
                    .findBySchemeDetailAndBenefitInAndJobGroup(schemeDetail, benefits, schemeMember.getJobGroup()).stream()
                    .collect(Collectors.toMap(l -> l.getBenefit().getCode(), l -> l));

            for (SchemeBenefitDTO sb : schemeBenefits) {
                String benefitCode = sb.getBenefitCode();
                Benefit benefit = benefitMap.get(benefitCode);

                if (benefit == null) {
                    log.warn("Benefit not found: {}", benefitCode);
                    continue;
                }

                if (existingBenefitCodes.contains(benefitCode)) {
                    log.info("SchemeMemberLimit already exists for benefit: {}", benefitCode);
                    continue;
                }

                SchemeBenefitLimit schemeBenefitLimit = benefitLimitMap.get(benefitCode);
                if (schemeBenefitLimit == null) {
                    log.warn("SchemeBenefitLimit not found for scheme: {}, benefit: {}, jobGroup: {}",
                            schemeDetail.getSystemCode(), benefitCode,
                            schemeMember.getJobGroup() != null ? schemeMember.getJobGroup().getCode() : "null");
                    continue;
                }

                if (!"P".equals(dto.getMemberType()) && schemeBenefitLimit.getIsLimitShared()) {
                    log.info("Limit shared and Member is not principal: {}", dto.getMemberType());
                    continue;
                }
                SchemeMemberLimit sml = getSchemeMemberLimit(sb, schemeDetail, schemeMember, benefit, schemeBenefitLimit);
                schemeMembers.add(sml);
            }

            log.info("Saved {} SchemeMemberLimits for scheme {}", schemeMembers.size(), schemeCode);
        }

        log.info("Eligibility processing completed for: {}", dto.getFullName());

        return saveSchemeMemberLimit(schemeMembers);
    }


    @Transactional
    private List<SchemeMemberLimit> saveSchemeMemberLimit(List<SchemeMemberLimit> schemeMembers) {
        return schemeMemberLimitRepository.saveAll(schemeMembers);
    }

    private SchemeMemberLimit getSchemeMemberLimit(SchemeBenefitDTO sb, SchemeDetail schemeDetail, SchemeMember schemeMember, Benefit benefit, SchemeBenefitLimit schemeBenefitLimit) {
        SchemeMemberLimit sml = new SchemeMemberLimit();
        sml.setSchemeDetail(schemeDetail);
        sml.setSchemeMember(schemeMember);
        sml.setBenefit(benefit);
        sml.setIsLimitShared(schemeBenefitLimit.getIsLimitShared());
        sml.setIsLimited(schemeBenefitLimit.getIsLimited());
        sml.setLimitAmount(Optional.ofNullable(schemeBenefitLimit.getLimitAmount()).orElse(0.0));
        sml.setLimitUtilized(sb.getUsedAmount() != null ? sb.getUsedAmount().doubleValue() : 0.0);
        double balance = Math.max(0.0, sml.getLimitAmount() - sml.getLimitUtilized());
        sml.setLimitBalance(balance);
        return sml;
    }

    private SchemeMember getSchemeMember(EligibilityResponseDTO dto, Member patient, SchemeDetail schemeDetail) {
        return schemeMemberRepository
                .findByPatientIdAndSchemeDetail(patient.getId().toString(), schemeDetail)
                .orElseGet(() -> {
                    SchemeMember sm = new SchemeMember();
                    if (dto.getEmployerDetails().getJobGroup() != null) {
                        jobGroupRepository.findByCode(dto.getEmployerDetails().getJobGroup())
                                .ifPresentOrElse(
                                        sm::setJobGroup,
                                        () -> log.warn("JobGroup not found: {}", dto.getEmployerDetails().getJobGroup())
                                );
                    }

                    if (dto.getMemberType() != null) {
                        memberRelationRepository.findByCode(dto.getMemberType())
                                .ifPresentOrElse(
                                        sm::setMemberRelation,
                                        () -> log.warn("MemberRelation not found: {}", dto.getMemberType())
                                );
                    }

                    sm.setPatientId(patient.getId().toString());
                    sm.setSchemeDetail(schemeDetail);
                    sm.setMemberNumber(dto.getMemberCrNumber());
                    sm.setPrincipalMemberNumber(dto.getPrincipalMemberCrNumber());
                    return schemeMemberRepository.save(sm);
                });
    }

    private Member getPatient(EligibilityResponseDTO dto) {
        return patientRepository.findByCrIdOrNationalId(
                        dto.getMemberCrNumber(), dto.getRequestIdNumber())
                .orElseGet(() -> {
                    Member m = new Member();
                    m.setCrId(dto.getMemberCrNumber());
                    m.setName(dto.getFullName());
                    m.setGender(dto.getGender());
                    m.setBirthDate(dto.getDateOfBirth());
                    m.setNationalId(dto.getRequestIdNumber());
                    return patientRepository.save(m);
                });
    }


    /**
     * Converts a CSV record to an EligibilityResponseDTO. Used for bulk
     * eligibility uploads.
     *
     * @param record CSVRecord containing eligibility data.
     * @return EligibilityResponseDTO instance.
     * @throws Exception if parsing fails.
     */
    public static EligibilityResponseDTO fromCsvRecord(CSVRecord record) throws Exception {
        EligibilityResponseDTO dto = new EligibilityResponseDTO();
        dto.setRequestIdNumber(record.get("IDNumber"));
        dto.setMemberCrNumber(record.get("CRNumber"));
        dto.setPrincipalMemberCrNumber(record.get("PrincipalCRNumber"));
        dto.setFullName(record.get("FullName"));
        dto.setMemberType(record.get("MemberRelationCode"));
        dto.setGender(record.get("Gender"));
        dto.setDateOfBirth(record.get("DateOfBirth") != null ? new java.text.SimpleDateFormat("dd/MM/yyyy").parse(record.get("DateOfBirth")) : null);

        EmployerDetailsDTO employer = new EmployerDetailsDTO();
        employer.setJobGroup(record.get("JobGroup"));
        dto.setEmployerDetails(employer);

        // Build benefit and scheme benefit DTOs from CSV
        EligibilityBenefitDTO benefit = new EligibilityBenefitDTO();
        benefit.setSchemeCode(record.get("SchemeCode"));

        ArrayList<SchemeBenefitDTO> schemeBenefits = new ArrayList<>();
        String[] benefitCodes = {
                Constants.BenefitCode.INPATIENT,
                Constants.BenefitCode.OUTPATIENT,
                Constants.BenefitCode.MATERNITY,
                Constants.BenefitCode.OPTICAL,
                Constants.BenefitCode.DENTAL
        };
        String[] usedAmountKeys = {
                Constants.BenefitUsedAmount.INPATIENT,
                Constants.BenefitUsedAmount.OUTPATIENT,
                Constants.BenefitUsedAmount.MATERNITY,
                Constants.BenefitUsedAmount.OPTICAL,
                Constants.BenefitUsedAmount.DENTAL
        };

        for (int i = 0; i < benefitCodes.length; i++) {
            SchemeBenefitDTO schemeBenefit = new SchemeBenefitDTO();
            schemeBenefit.setBenefitCode(benefitCodes[i]);
            schemeBenefit.setUsedAmount(Integer.parseInt(record.get(usedAmountKeys[i])));
            schemeBenefits.add(schemeBenefit);
        }
        benefit.setSchemeBenefits(schemeBenefits);
        dto.setBenefits(List.of(benefit));

        return dto;
    }

    /**
     * Handles uploading and processing of eligibility data from a CSV file.
     * Each record is converted to a DTO and processed.
     *
     * @param file MultipartFile containing the CSV data.
     * @return Status message.
     */
    public String uploadEligibilityFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new EmptyFileException("File is empty");
        }
        try {
            List<CSVRecord> records = CSVFormat.DEFAULT
                    .builder().setHeader().setSkipHeaderRecord(true).build()
                    .parse(new java.io.InputStreamReader(file.getInputStream()))
                    .getRecords();

            for (CSVRecord record : records) {
                EligibilityResponseDTO dto = fromCsvRecord(record);
                processEligibility(dto);
            }
        } catch (Exception e) {
            throw new EligibilityFileProcessingException("Error processing file", e);
        }
        return "File uploaded successfully: ";
    }

}
