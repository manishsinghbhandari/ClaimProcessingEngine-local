package io.coredge.health.claims.db.entity.BMS;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "system_codes", schema = "public")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SystemCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "prefix")
    private String prefix;

    @Column(name = "start_point")
    private Integer startPoint;
}