package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.WorkFlow;

import java.util.List;
import java.util.Optional;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkFlowRepository extends JpaRepository<WorkFlow, Long> {

  @Query("SELECT CASE WHEN EXISTS (SELECT 1 FROM WorkFlow c WHERE c.jobKey = :jobKey) THEN true ELSE false END")
  Optional<Boolean> existsByJobKey(@Param("jobKey") Long jobKey);

  @Query("SELECT CASE WHEN EXISTS (SELECT 1 FROM WorkFlow c WHERE c.claimId = :claimId AND c.active = true) THEN true ELSE false END")
  Optional<Boolean> existsByClaimId(@Param("claimId") String claimId);

  Optional<List<WorkFlow>> findAllByClaimIdAndActiveIsTrue(String claimId);

  Optional<WorkFlow> findByClaimIdAndProcessInstanceKey(String claimId, Long processInstanceKey);

  Optional<List<WorkFlow>> findAllByClaimIdInAndActiveIsTrue(List<String> claimIds);

  @Transactional
  @Modifying
  @Query("""
    UPDATE WorkFlow w 
    SET w.active = false 
    WHERE w.id IN :workflowIds
    """)
  void deleteAllByIdsNative(@Param("workflowIds") List<Long> workflowIds);
}
