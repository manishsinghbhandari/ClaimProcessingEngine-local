package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.BenefitsMaster;
import io.coredge.health.claims.db.entity.Member;
import io.coredge.health.claims.db.entity.Provider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MemberRepository extends JpaRepository<Member, UUID> {

  Optional<List<Member>> findByCrId(String crId);

  Optional<List<Member>> findByNationalId(String nationalId);

  Optional<Boolean> existsByCrId(String crId);

  Optional<Boolean> existsByNationalId(String nationalId);

  @Query("SELECT p FROM Member p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :searchKey, '%')) " +
          "AND p.id IN (SELECT MIN(p2.id) FROM Member p2 GROUP BY p2.name)")
  Optional<Page<Member>> findDistinctByNameContainingIgnoreCase(String searchKey, Pageable page);

  Optional<Member> findByShaNumber(String id);

  Optional<Member> getByCrId(String id);

  Optional<Member> findByCrIdOrShaNumber(String crId , String shaNumber);

    //  @Query("SELECT p FROM Provider p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :searchKey, '%')) GROUP BY p.name")
//  List<Provider> findDistinctByNameContainingIgnoreCase(@Param("searchKey") String searchKey);

  @Query("SELECT m.shaNumber FROM Member m")List<String> findAllShaNumbers();

  Optional<Member> findByCrIdOrNationalId(String crId, String nationalId);

    @Query(value = "SELECT * FROM public.patient p " +
            "WHERE (:name IS NULL OR LOWER(CAST(p.name AS TEXT)) LIKE LOWER(CONCAT('%', :name, '%'))) " +
            "AND (:gender IS NULL OR LOWER(CAST(p.gender AS TEXT)) = LOWER( :gender )) " +
            "AND (:identifier IS NULL OR p.cr_id = :identifier) " +
            "AND p.id IN (SELECT MIN(CAST(p2.id AS TEXT))::UUID FROM public.patient p2 GROUP BY p2.name)",
            countQuery = "SELECT COUNT(*) FROM public.patient p " +
                    "WHERE (:name IS NULL OR LOWER(CAST(p.name AS TEXT)) LIKE LOWER(CONCAT('%', :name, '%'))) " +
                    "AND (:gender IS NULL OR p.gender = :gender) " +
                    "AND (:identifier IS NULL OR p.cr_id = :identifier)",
            nativeQuery = true)
    Page<Member> findBenefitsByAllOptionalParams(@Param("gender") String gender,
                                                 @Param("name") String name,
                                                 @Param("identifier") String identifier, Pageable pageable
    );
}

