package io.coredge.health.claims.rest;

import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.models.ProcessNotesDTO;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

public interface ProcessNotesController {


    ResponseEntity<List<ProcessNotesDTO>> getProcessNotes(String claimId);

    ResponseEntity<String> addProcessNotesHistory(ExtensionStateCodeEnum status, String use, int count);

}
