package io.coredge.health.claims.db.entity.BMS;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "job_groups", schema = "public")
public class JobGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code")
    private String code;

    @Column(name = "name")
    private String name;

}
