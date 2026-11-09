package io.coredge.health.claims.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hl7.fhir.r4.model.ClaimResponse;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProcessNotesAuditInfo {
    private ClaimResponse.NoteComponent noteComponent;
    private String lastModifiedBy;
    private LocalDateTime lastModifiedDate;
}
