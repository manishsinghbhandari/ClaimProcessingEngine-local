package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.InterventionDuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface InterventionDurationRepository extends JpaRepository<InterventionDuration, Long> {

    @Query(value = "SELECT * FROM intervention_duration ic WHERE :inputCode LIKE '%' || ic.code || '%'", nativeQuery = true)
    List<InterventionDuration> findCodeThatSubStringOf(@Param("inputCode") String inputCode);

    default List<InterventionDuration> findCodesThatAreSubstringsOf(@Param("inputCodes") Set<String> inputCodes){
        String[] array = inputCodes.toArray(new String[0]);
        return findCodesThatAreSubstringsOf(array);
    }

    @Query(value = """
    SELECT * 
    FROM intervention_duration id 
    WHERE EXISTS (
        SELECT 1 
        FROM unnest(:inputCodes) AS inputCode 
        WHERE inputCode LIKE '%' || id.code || '%'
    )
""", nativeQuery = true)
    List<InterventionDuration> findCodesThatAreSubstringsOf(@Param("inputCodes") String[] inputCodes);

}
