package io.coredge.health.claims.db.entity.BMS;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "claim_statuses", schema = "public")
public class ClaimStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "auto_number")
    private Integer autoNumber;

    @Column(name = "auto_code")
    private String autoCode;

    @Column(name = "name")
    private String name;

    @Column(name = "operation")
    private String operation;

    @Column(name = "amount_column")
    private String amountColumn;

}
