/**
 * Author: Reinhard Odhiambo
 *
 * This service class handles operations related to Scheme Members in the BMS (Benefit Management System).
 * It provides methods to create, update, delete, and retrieve Scheme Members and their associated data.
 * It also includes methods to retrieve principal members and their dependents, along with their benefit limits
 */
package io.coredge.health.claims.service.BMS;

import io.coredge.health.claims.db.entity.Member;
import io.coredge.health.claims.db.entity.BMS.*;
import io.coredge.health.claims.db.repositories.MemberRepository;
import io.coredge.health.claims.db.repositories.BMS.*;

import io.coredge.health.claims.dto.SchemeMemberDTO;
import io.coredge.health.claims.dto.SchemeMemberListDTO;
import io.coredge.health.claims.dto.SchemeMemberLimitDTO;
import io.coredge.health.claims.dto.SchemeMemberWithLimitsDTO;
import io.coredge.health.claims.dto.BenefitDTO;
import io.coredge.health.claims.dto.MemberRelationDTO;
import io.coredge.health.claims.dto.PrincipalAndDependentsDTO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import gen.lib.cdt.dtopen__c;

@Service
@AllArgsConstructor
@Slf4j
public class SchemeMemberService {
    private final SchemeMemberRepository schemeMemberRepository;
    private final SchemeDetailRepository schemeDetailRepository;
    private final JobGroupRepository jobGroupRepository;
    private final MemberRelationRepository memberRelationRepository;
    private final SchemeMemberLimitRepository schemeMemberLimitRepository;
    private final MemberRepository patientRepository;


    /**
     * Creates a new SchemeMember from a DTO.
     *
     * @param dto Data transfer object containing member details.
     * @return The saved SchemeMember entity.
     */
    public SchemeMember create(SchemeMemberDTO dto) {
        SchemeMember member = new SchemeMember();
        // Set scheme detail, patient, member numbers, job group, and relation
        member.setSchemeDetail(schemeDetailRepository.findById(dto.getScheme_detail_id()).orElseThrow());
        member.setPatientId(dto.getPatient_id());
        member.setMemberNumber(dto.getMember_number());
        member.setPrincipalMemberNumber(dto.getPrincipal_member_number());
        if (dto.getJob_group_id() != null) {
            member.setJobGroup(jobGroupRepository.findById(dto.getJob_group_id()).orElse(null));
        }
        if (dto.getMember_relation_id() != null) {
            member.setMemberRelation(memberRelationRepository.findById(dto.getMember_relation_id()).orElse(null));
        }
        // Set other fields as needed
        return schemeMemberRepository.save(member);
    }

    /**
     * Finds a SchemeMember by its ID.
     *
     * @param id The member's ID.
     * @return Optional containing the member if found.
     */
    public Optional<SchemeMember> findById(Long id) {
        return schemeMemberRepository.findById(id);
    }

    /**
     * Updates an existing SchemeMember with new data from a DTO.
     *
     * @param id The member's ID.
     * @param dto The DTO with updated data.
     * @return The updated SchemeMember entity.
     */
    public SchemeMember update(Long id, SchemeMemberDTO dto) {
        SchemeMember member = schemeMemberRepository.findById(id).orElseThrow();
        member.setSchemeDetail(schemeDetailRepository.findById(dto.getScheme_detail_id()).orElseThrow());
        member.setPatientId(dto.getPatient_id());
        member.setMemberNumber(dto.getMember_number());
        member.setPrincipalMemberNumber(dto.getPrincipal_member_number());
        if (dto.getJob_group_id() != null) {
            member.setJobGroup(jobGroupRepository.findById(dto.getJob_group_id()).orElse(null));
        }
        if (dto.getMember_relation_id() != null) {
            member.setMemberRelation(memberRelationRepository.findById(dto.getMember_relation_id()).orElse(null));
        }
        // Set other fields as needed
        return schemeMemberRepository.save(member);
    }

    /**
     * Deletes a SchemeMember by its ID.
     *
     * @param id The member's ID.
     */
    public void delete(Long id) {
        schemeMemberRepository.deleteById(id);
    }

    /**
     * Retrieves all SchemeMember entities.
     *
     * @return Iterable of all members.
     */
    public Iterable<SchemeMember> findAll() {
        return schemeMemberRepository.findAll();
    }

    /**
     * Retrieves all SchemeMembers as DTOs.
     *
     * @return List of SchemeMemberListDTO.
     */
    public List<SchemeMemberListDTO> findAllDTO() {
        return schemeMemberRepository.findAll().stream().map(member -> {
            SchemeMemberListDTO dto = new SchemeMemberListDTO();
            dto.setId(member.getId());
            dto.setSchemeDetailId(member.getSchemeDetail() != null ? member.getSchemeDetail().getId() : null);
            dto.setPatientId(member.getPatientId());
            dto.setMemberNumber(member.getMemberNumber());
            dto.setPrincipalMemberNumber(member.getPrincipalMemberNumber());
            dto.setJobGroupId(member.getJobGroup() != null ? member.getJobGroup().getId() : null);
            dto.setMemberRelationId(member.getMemberRelation() != null ? member.getMemberRelation().getId() : null);
            // Set other fields as needed
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * Retrieves a paginated list of SchemeMembers as DTOs.
     *
     * @param pageable Pagination information.
     * @return Page of SchemeMemberListDTO.
     */
    public Page<SchemeMemberListDTO> findAllDTO(Pageable pageable) {
        return schemeMemberRepository.findAll(pageable)
                .map(member -> {
                    // Fetch patient details for additional info
                    Member patient = patientRepository.findById(UUID.fromString(member.getPatientId())).orElse(null);
                    SchemeMemberListDTO dto = new SchemeMemberListDTO();
                    dto.setId(member.getId());
                    dto.setSchemeDetailId(member.getSchemeDetail() != null ? member.getSchemeDetail().getId() : null);
                    dto.setPatientId(member.getPatientId());
                    dto.setMemberNumber(member.getMemberNumber());
                    dto.setPrincipalMemberNumber(member.getPrincipalMemberNumber());
                    dto.setJobGroupId(member.getJobGroup() != null ? member.getJobGroup().getId() : null);
                    dto.setMemberRelationId(member.getMemberRelation() != null ? member.getMemberRelation().getId() : null);
                    dto.setMemberNames(patient != null ? patient.getName() : null);
                    dto.setJobGroup(member.getJobGroup() != null ? member.getJobGroup().getName() : null);
                    dto.setNationalId(patient != null ? patient.getNationalId() : null);
                    dto.setHouseholdNo(patient != null ? patient.getHouseholdNumber() : null);
                    // Set other fields as needed
                    return dto;
                });
    }

    /**
     * Retrieves the principal member and their dependents (with limits) for a
     * given member ID.
     *
     * @param memberId The member's ID.
     * @return DTO containing principal and dependents with their limits.
     */
    public PrincipalAndDependentsDTO getPrincipalAndDependentsWithLimits(Long memberId) {
        SchemeMember member = schemeMemberRepository.findById(memberId).orElse(null);
        if (member == null) {
            return null;
        }
        log.info("Fetching principal and dependents for member: {}", member.getMemberNumber());
        SchemeMember principal = schemeMemberRepository
                .findPrincipalMember(member.getSchemeDetail().getId(), member.getPrincipalMemberNumber())
                .orElse(member);
        // Get dependents: same schemeDetail, principalMemberNumber = principal.memberNumber, and not the principal
        List<SchemeMember> dependents = schemeMemberRepository.findAll().stream()
                .filter(m -> m.getSchemeDetail().getId().equals(principal.getSchemeDetail().getId()))
                .filter(m -> principal.getMemberNumber().equals(m.getPrincipalMemberNumber()))
                .filter(m -> !m.getMemberNumber().equals(m.getPrincipalMemberNumber()))
                .collect(Collectors.toList());

        // Map principal and dependents to DTOs
        SchemeMemberWithLimitsDTO principalDTO = mapToMemberWithLimitsDTO(principal);
        List<SchemeMemberWithLimitsDTO> dependentsDTO = dependents.stream()
                .map(this::mapToMemberWithLimitsDTO)
                .collect(Collectors.toList());
        log.info("Found {} dependents for principal member: {}", dependentsDTO.size(), principalDTO.getMemberNumber());
        PrincipalAndDependentsDTO dto = new PrincipalAndDependentsDTO();
        dto.setPrincipal(principalDTO);
        dto.setDependents(dependentsDTO);
        return dto;
    }

    /**
     * Maps a SchemeMember entity to a DTO including their benefit limits.
     *
     * @param member The SchemeMember entity.
     * @return DTO with member and limit details.
     */
    private SchemeMemberWithLimitsDTO mapToMemberWithLimitsDTO(SchemeMember member) {
        SchemeMemberWithLimitsDTO dto = new SchemeMemberWithLimitsDTO();
        Member patient = null;
        try {
            UUID patientUuid = UUID.fromString(member.getPatientId());
            patient = patientRepository.findById(patientUuid).orElse(null);
        } catch (IllegalArgumentException e) {
            // Handle invalid UUID format if needed
        }
        if (patient != null) {
            dto.setBirthDate(patient.getBirthDate() != null ? patient.getBirthDate().toString() : null);
            dto.setGender(patient.getGender());
            dto.setName(patient.getName());
        }
        dto.setId(member.getId());
        dto.setPatientId(member.getPatientId());
        dto.setMemberNumber(member.getMemberNumber());
        dto.setPrincipalMemberNumber(member.getPrincipalMemberNumber());
        dto.setSchemeDetailId(member.getSchemeDetail() != null ? member.getSchemeDetail().getId() : null);
        dto.setRelation(member.getMemberRelation() != null ? mapToMemberRelationDTO(member.getMemberRelation()) : null);
        dto.setJobGroup(member.getJobGroup() != null ? member.getJobGroup().getName() : null);
        dto.setNationalId(patient != null ? patient.getNationalId() : null);
        dto.setHouseholdNo(patient != null ? patient.getHouseholdNumber() : null);
        dto.setCrNumber(patient != null ? patient.getCrId() : null);

        // Map all benefit limits for this member
        List<SchemeMemberLimitDTO> limits = schemeMemberLimitRepository.findBySchemeMemberId(member.getId())
                .stream()
                .map(this::mapToLimitDTO)
                .collect(Collectors.toList());
        dto.setLimits(limits);

        return dto;
    }

    /**
     * Maps a SchemeMemberLimit entity to its DTO.
     *
     * @param limit The SchemeMemberLimit entity.
     * @return DTO with limit details.
     */
    private SchemeMemberLimitDTO mapToLimitDTO(SchemeMemberLimit limit) {
        SchemeMemberLimitDTO dto = new SchemeMemberLimitDTO();
        dto.setId(limit.getId());
        dto.setBenefit(limit.getBenefit() != null ? mapToBenefitDTO(limit.getBenefit()) : null);
        dto.setIsLimitShared(limit.getIsLimitShared());
        dto.setIsLimited(limit.getIsLimited());
        dto.setLimitAmount(limit.getLimitAmount());
        dto.setLimitUtilized(limit.getLimitUtilized());
        dto.setLimitOverUtilized(limit.getLimitOverUtilized());
        dto.setLimitBalance(limit.getLimitBalance());
        return dto;
    }

    /**
     * Maps a Benefit entity to its DTO.
     *
     * @param benefit The Benefit entity.
     * @return DTO with benefit details.
     */
    private BenefitDTO mapToBenefitDTO(Benefit benefit) {
        BenefitDTO dto = new BenefitDTO();
        dto.setId(benefit.getId());
        dto.setCode(benefit.getCode());
        dto.setName(benefit.getName());
        // Map other fields as needed
        return dto;
    }

    /**
     * Maps a MemberRelation entity to its DTO.
     *
     * @param memberRelation The MemberRelation entity.
     * @return DTO with relation details.
     */
    private MemberRelationDTO mapToMemberRelationDTO(MemberRelation memberRelation) {
        MemberRelationDTO dto = new MemberRelationDTO();
        dto.setId(memberRelation.getId());
        dto.setCode(memberRelation.getCode());
        dto.setName(memberRelation.getName());
        return dto;
    }
}
