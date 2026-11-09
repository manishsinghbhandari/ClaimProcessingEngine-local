package io.coredge.health.claims.db.repositories.BMS;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.coredge.health.claims.db.entity.BMS.MemberRelation;

@Repository
public interface MemberRelationRepository extends JpaRepository<MemberRelation, Long> {
    Optional<MemberRelation> findByCode(String code);
}