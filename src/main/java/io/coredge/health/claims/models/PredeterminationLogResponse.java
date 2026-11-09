package io.coredge.health.claims.models;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class PredeterminationLogResponse {

    private Long id;
    private String claimId;
    private Predetermination input;
    private boolean status;
    private List<String> reason;
    private LocalDateTime creationDate;

}
