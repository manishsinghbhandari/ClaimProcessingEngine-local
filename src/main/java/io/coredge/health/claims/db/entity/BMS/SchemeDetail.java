package io.coredge.health.claims.db.entity.BMS;

import io.coredge.health.claims.db.entity.BMS.common.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Table(
        name = "scheme_details",
        schema = "public",
        indexes = {
            @Index(name = "idx_sdetails_status_id", columnList = "status_id"),
            @Index(name = "idx_sdetails_created_by", columnList = "created_by"),
            @Index(name = "idx_sdetails_updated_by", columnList = "updated_by")
        }
)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SchemeDetail extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "auto_number", nullable = false)
    private Integer autoNumber;

    @Column(name = "auto_code", nullable = false)
    private String autoCode;

    @Column(name = "system_code", nullable = true, unique = true)
    private String systemCode;

    @Column(name = "uuid", nullable = false, unique = true)
    private String uuid;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "start_date", nullable = false)
    private Date startDate;

    @Column(name = "end_date")
    private Date endDate;

    @Column(name = "family_size")
    private Integer familySize;

}
