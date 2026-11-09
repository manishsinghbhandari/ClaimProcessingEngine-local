package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.InsuranceClaim;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public class InsuranceClaimCustomRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public List<InsuranceClaim> findAssignedTasksClaims(
            String use,
            String status,
            String code,
            Boolean isAssigned,
            String whereQuery,
            List<UUID> claimIds,
            List<String> restrictedCodes,
            Long minimumClaimAgeInDays,
            Long limit,
            String sortBy
    ) {
        // Build native SQL query
        StringBuilder sql = new StringBuilder("""
            SELECT DISTINCT ic.*,
                   (select ci.code
                      from claim_items ci
                     where ci.claim_id = ic.claim_id
                     order by ci.sequence asc
                     limit 1) AS shaCode
            FROM claims ic
            LEFT JOIN claim_items item ON ic.claim_id = item.claim_id
            LEFT JOIN provider p ON p.id = ic.provider
            WHERE (:status IS NULL OR ic.status = :status)
              AND ic.is_phc_claim = false
              AND ic.use = :use
              AND ic.is_assigned = :isAssigned
              AND NOT EXISTS (
                      SELECT 1
                      FROM user_bucket ub
                      WHERE ub.claim_id = ic.claim_id
                        AND ub.claim_status = :status
                        AND ub.status IN ('PENDING', 'COMPLETED')
                  )
              AND NOT EXISTS (
                    SELECT 1
                    FROM process_notes pn
                    WHERE pn.claim_id = ic.external_id
                    AND pn.status = 'APPROVED'
                  )
            """);

        // Append code condition only if code is provided
        boolean hasClaimIds = claimIds != null && !claimIds.isEmpty();
        if (hasClaimIds) {
            sql.append(" AND ic.claim_id NOT IN (:claimIds) ");
        }

        // Append code condition only if code is provided
        boolean hasCode = code != null && !code.trim().isEmpty();
        if (hasCode) {
            sql.append(" AND UPPER(item.code) LIKE :code");
        }

        // Append code condition only if code is provided
        boolean hasRestrictedCodes = restrictedCodes != null && !restrictedCodes.isEmpty();
        if (hasRestrictedCodes) {
            sql.append(" AND item.code not IN :restrictedCodes ");
        }

        // Append code condition only if code is provided
        boolean hasMinimumClaimAgeInDays = minimumClaimAgeInDays != null && minimumClaimAgeInDays > 0L;
        if (hasMinimumClaimAgeInDays) {
            sql.append(" AND NOW() > ic.").append(sortBy).append(" + (:minimumClaimAgeInDays * INTERVAL '1 day')");
        }

        // Append dynamic whereQuery safely
        if (whereQuery != null && !whereQuery.isBlank()) {
            sql.append(" AND ").append(whereQuery);
        }

        sql.append(" ORDER BY ic.").append(sortBy).append(" ASC LIMIT :limit");

        // Create native query
        Query query = entityManager.createNativeQuery(sql.toString(), InsuranceClaim.class);

        // Set parameters
        query.setParameter("use", use);
        query.setParameter("status", status);
        query.setParameter("isAssigned", isAssigned);
        query.setParameter("limit", limit);

        if(hasClaimIds){
            query.setParameter("claimIds", claimIds);
        }

        // Set code parameter if present
        if (hasCode) {
            query.setParameter("code", code.trim().toUpperCase() + "%");
        }

        if(hasRestrictedCodes){
            query.setParameter("restrictedCodes", restrictedCodes);
        }

        if(hasMinimumClaimAgeInDays){
            query.setParameter("minimumClaimAgeInDays", minimumClaimAgeInDays);
        }

        return query.getResultList();
    }

}

