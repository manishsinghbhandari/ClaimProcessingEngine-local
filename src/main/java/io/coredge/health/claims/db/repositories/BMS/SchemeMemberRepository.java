package io.coredge.health.claims.db.repositories.BMS;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.coredge.health.claims.db.entity.BMS.SchemeDetail;
import io.coredge.health.claims.db.entity.BMS.SchemeMember;

@Repository
public interface SchemeMemberRepository extends JpaRepository<SchemeMember, Long> {
   

    Optional<SchemeMember> findByPatientIdAndSchemeDetail(String patientId, SchemeDetail schemeDetail);
    Optional<SchemeMember> findByMemberNumberAndSchemeDetail(String memberNumber, SchemeDetail schemeDetail);
    @Query("SELECT m FROM SchemeMember m " +
            "WHERE m.schemeDetail.id = :schemeId " +
            "AND m.memberNumber = :principalNumber " +
            "AND m.memberNumber = m.principalMemberNumber")
    Optional<SchemeMember> findPrincipalMember(
            @Param("schemeId") Long schemeId,
            @Param("principalNumber") String principalNumber
    );

}
