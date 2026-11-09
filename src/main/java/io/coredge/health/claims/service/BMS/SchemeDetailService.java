/**
 * Author: Reinhard Odhiambo
 * 
 * Service for managing SchemeDetail entities and their related job groups, benefits, and benefit limits.
 * Provides CRUD operations and handles the creation and update of scheme details with associated relations.
 */

package io.coredge.health.claims.service.BMS;

import io.coredge.health.claims.db.entity.BMS.*;
import io.coredge.health.claims.db.repositories.BMS.*;
import io.coredge.health.claims.dto.SchemeBenefitLimitDTO;
import io.coredge.health.claims.dto.SchemeDetailDTO;
import io.coredge.health.claims.dto.SchemeDetailResponseDTO;
import io.coredge.health.claims.dto.BenefitDTO;
import io.coredge.health.claims.dto.JobGroupDTO;
import io.coredge.health.claims.service.common.AutoNumberService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class SchemeDetailService {
    private SchemeDetailRepository schemeDetailRepository;
    private JobGroupRepository jobGroupRepository;
    private BenefitRepository benefitRepository;
    private SchemeJobGroupRepository schemeJobGroupRepository;
    private SchemeBenefitRepository schemeBenefitRepository;
    private SchemeBenefitLimitRepository schemeBenefitLimitRepository;
    private AutoNumberService autoNumberService;

    /**
     * Retrieves all scheme details with pagination.
     * @param pageable Pagination information.
     * @return Page of SchemeDetail entities.
     */
    public Page<SchemeDetail> findAll(Pageable pageable) {
        return schemeDetailRepository.findAll(pageable);
    }

    /**
     * Retrieves a scheme detail by its ID, including its job groups and benefit limits.
     * @param id The scheme detail ID.
     * @return Optional containing SchemeDetailResponseDTO if found.
     */
    public Optional<SchemeDetailResponseDTO> findById(Long id) {
        Optional<SchemeDetail> optionalSchemeDetail = schemeDetailRepository.findById(id);
        if (optionalSchemeDetail.isEmpty()) {
            return Optional.empty();
        }
        SchemeDetail schemeDetail = optionalSchemeDetail.get();

        // Fetch related benefit limits and job groups
        List<SchemeBenefitLimit> limits = schemeBenefitLimitRepository.findBySchemeDetail(schemeDetail);
        List<SchemeJobGroup> jobGroups = schemeJobGroupRepository.findBySchemeDetailId(schemeDetail.getId());

        // Build response DTO with scheme details, job groups, and benefit limits
        SchemeDetailResponseDTO responseDTO = new SchemeDetailResponseDTO();
        responseDTO.setSchemeDetail(SchemeDetailDTO.builder()
                .id(schemeDetail.getId())
                .autoNumber(schemeDetail.getAutoNumber())
                .autoCode(schemeDetail.getAutoCode())
                .systemCode(schemeDetail.getSystemCode())
                .uuid(schemeDetail.getUuid())
                .name(schemeDetail.getName())
                .startDate(schemeDetail.getStartDate())
                .endDate(schemeDetail.getEndDate())
                .familySize(schemeDetail.getFamilySize())
                .build());

        // Map benefit limits to DTOs
        List<SchemeBenefitLimitDTO> benefitLimits = limits.stream()
            .map((SchemeBenefitLimit limit) -> SchemeBenefitLimitDTO.builder()
                .benefitId(limit.getId())
                .benefit(BenefitDTO.builder()
                    .id(limit.getBenefit().getId())
                    .name(limit.getBenefit().getName())
                    .code(limit.getBenefit().getCode())
                    .build())
                .jobGroup(limit.getJobGroup() != null ? 
                    JobGroupDTO.builder()
                        .id(limit.getJobGroup().getId())
                        .name(limit.getJobGroup().getName())
                        .build() : null)
                .isLimitShared(limit.getIsLimitShared())
                .isLimited(limit.getIsLimited())
                .limitAmount(limit.getLimitAmount())
                .build())
            .collect(Collectors.toList());

        // Map job groups to DTOs
        responseDTO.setJobGroups(jobGroups.stream()
            .map(sjg -> JobGroupDTO.builder()
                .id(sjg.getJobGroup().getId())
                .name(sjg.getJobGroup().getName())
                .build())
            .collect(Collectors.toList()));

        responseDTO.setBenefitLimits(benefitLimits);
        return Optional.of(responseDTO);
    }

    /**
     * Saves a SchemeDetail entity to the database.
     * @param schemeDetail The SchemeDetail entity.
     * @return The saved SchemeDetail.
     */
    public SchemeDetail save(SchemeDetail schemeDetail) {
        return schemeDetailRepository.save(schemeDetail);
    }

    /**
     * Deletes a SchemeDetail by its ID.
     * @param id The scheme detail ID.
     */
    public void deleteById(Long id) {
        schemeDetailRepository.deleteById(id);
    }

    /**
     * Creates or updates a SchemeDetail and its related job groups, benefits, and benefit limits.
     * Handles both insert and update logic, including removal of old relations on update.
     * @param dto The SchemeDetailDTO containing scheme and relation data.
     * @return The saved SchemeDetail entity.
     */
    @Transactional
    public SchemeDetail createOrUpdateSchemeDetail(SchemeDetailDTO dto) {
        // If updating, fetch existing; otherwise, create new
        SchemeDetail tempSchemeDetail = dto.getId() != null ?
            schemeDetailRepository.findById(dto.getId()).orElse(new SchemeDetail()) :
            new SchemeDetail();

        // Set auto-generated fields using the last auto number
        Integer lastAutoNumber = schemeDetailRepository.findMaxAutoNumber();
        autoNumberService.setAutoFields(tempSchemeDetail, lastAutoNumber);

        // Set main scheme detail fields
        tempSchemeDetail.setName(dto.getName());
        tempSchemeDetail.setStartDate(dto.getStartDate());
        tempSchemeDetail.setEndDate(dto.getEndDate());
        tempSchemeDetail.setFamilySize(dto.getFamilySize());
        tempSchemeDetail.setSystemCode(dto.getSystemCode());

        // Save the scheme detail
        SchemeDetail schemeDetail = schemeDetailRepository.save(tempSchemeDetail);

        // If updating, remove old job groups, benefits, and limits
        if (dto.getId() != null) {
            schemeJobGroupRepository.deleteBySchemeDetail(schemeDetail);
            schemeBenefitRepository.deleteBySchemeDetail(schemeDetail);
            schemeBenefitLimitRepository.deleteAll(
                schemeBenefitLimitRepository.findAll().stream()
                    .filter(limit -> limit.getSchemeDetail().getId().equals(schemeDetail.getId()))
                    .collect(Collectors.toList())
            );
        }

        // Add new job groups
        List<JobGroup> jobGroups = jobGroupRepository.findAllById(dto.getJobGroupIds());
        List<SchemeJobGroup> sjgs = jobGroups.stream()
            .map(jg -> SchemeJobGroup.builder().schemeDetail(schemeDetail).jobGroup(jg).build())
            .collect(Collectors.toList());
        schemeJobGroupRepository.saveAll(sjgs);

        // Add new benefits and limits
        List<Long> benefitIds = dto.getBenefitLimits().stream()
            .map(SchemeBenefitLimitDTO::getBenefitId)
            .collect(Collectors.toList());
        List<Benefit> benefits = benefitRepository.findAllById(benefitIds);

        List<SchemeBenefit> sbs = benefits.stream()
            .map(b -> SchemeBenefit.builder().schemeDetail(schemeDetail).benefit(b).build())
            .collect(Collectors.toList());
        schemeBenefitRepository.saveAll(sbs);

        // Add benefit limits for each benefit/job group combination
        List<SchemeBenefitLimit> limits = new ArrayList<>();
        for (SchemeBenefitLimitDTO limitDTO : dto.getBenefitLimits()) {
            Benefit benefit = benefits.stream()
                .filter(b -> b.getId().equals(limitDTO.getBenefitId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid benefitId: " + limitDTO.getBenefitId()));

            JobGroup jobGroup = null;
            if (limitDTO.getJobGroupId() != null) {
                jobGroup = jobGroupRepository.findById(limitDTO.getJobGroupId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid jobGroupId: " + limitDTO.getJobGroupId()));
            }

            SchemeBenefitLimit limit = SchemeBenefitLimit.builder()
                .schemeDetail(schemeDetail)
                .benefit(benefit)
                .jobGroup(jobGroup)
                .isLimitShared(limitDTO.getIsLimitShared())
                .isLimited(limitDTO.getIsLimited())
                .limitAmount(limitDTO.getLimitAmount())
                .build();
            limits.add(limit);
        }
        schemeBenefitLimitRepository.saveAll(limits);

        return schemeDetail;
    }

    /**
     * Retrieves all benefits for a given scheme ID.
     * @param schemeId The scheme detail ID.
     * @return List of BenefitDTOs.
     */
    public List<BenefitDTO> getBenefitsBySchemeId(Long schemeId) {
        List<SchemeBenefit> schemeBenefits = schemeBenefitRepository.findBySchemeDetailId(schemeId);
        return schemeBenefits.stream()
                .map(sb -> {
                    Benefit b = sb.getBenefit();
                    return new BenefitDTO(b.getId(), b.getName());
                })
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all job groups for a given scheme ID.
     * @param schemeId The scheme detail ID.
     * @return List of JobGroupDTOs.
     */
    public List<JobGroupDTO> getJobGroupsBySchemeId(Long schemeId) {
        List<SchemeJobGroup> schemeJobGroups = schemeJobGroupRepository.findBySchemeDetailId(schemeId);
        return schemeJobGroups.stream()
                .map(sjg -> {
                    JobGroup jg = sjg.getJobGroup();
                    return new JobGroupDTO(jg.getId(), jg.getName());
                })
                .collect(Collectors.toList());
    }
}