package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.ProcessNotes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProcessNotesRepository extends JpaRepository<ProcessNotes, Long> {

    Optional<List<ProcessNotes>> findByClaimId(String claimId);

    @Query("SELECT MAX(p.sequenceNo) FROM ProcessNotes p WHERE p.claimId = :claimId")
    Integer findMaxSequenceNoByClaimId(String claimId);


}
