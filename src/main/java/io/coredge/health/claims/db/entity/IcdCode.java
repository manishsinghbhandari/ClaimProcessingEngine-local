package io.coredge.health.claims.db.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ICD Code entity for PostgreSQL - migrated from MongoDB
 * Used for PHC payment calculations based on ICD 10 and ICD 11 codes
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "icd_codes")
public class IcdCode extends AuditableBase<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "icd_10_disease_code", nullable = false, unique = true, length = 255)
    private String icd10DiseaseCode;

    @Column(name = "icd_11_codes", length = 255)
    private String icd11Codes;

    @Column(name = "main_code", length = 255)
    private String mainCode;

    @Column(name = "sub_code", length = 255)
    private String subCode;

    @Column(name = "weight", nullable = false, precision = 10, scale = 2)
    private BigDecimal weight;

}
