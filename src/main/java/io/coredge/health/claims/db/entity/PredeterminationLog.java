package io.coredge.health.claims.db.entity;

import io.coredge.health.claims.db.repositories.converter.JsonConverter;
import io.coredge.health.claims.db.repositories.converter.StringListConverter;
import io.coredge.health.claims.models.Predetermination;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Setter
@Getter
@Builder
@Table(name = "predetermination_log", schema = "public")
@NoArgsConstructor
@AllArgsConstructor
public class PredeterminationLog extends AuditableBase<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "claim_id" , nullable = false)
    private String claimId;

    @Convert(converter = JsonConverter.class)
    @Column(name = "input" , nullable = false , columnDefinition = "TEXT")
    private Predetermination input;

    @Column(name = "status" , nullable = false)
    private boolean status;

    @Convert(converter = StringListConverter.class)
    @Column(name = "reason", columnDefinition = "TEXT")
    private List<String> reason = new ArrayList<>();

}
