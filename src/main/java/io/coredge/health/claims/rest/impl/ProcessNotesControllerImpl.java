package io.coredge.health.claims.rest.impl;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.enums.ProviderSearchType;
import io.coredge.health.claims.helpers.Helper;
import io.coredge.health.claims.models.ProcessNotesDTO;
import io.coredge.health.claims.models.master.ProviderResponse;
import io.coredge.health.claims.models.master.SuggestionResponse;
import io.coredge.health.claims.rest.ProcessNotesController;
import io.coredge.health.claims.rest.ProviderController;
import io.coredge.health.claims.service.ProcessNotesService;
import io.coredge.health.claims.service.ProviderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping(Constants.EndPoints.PROCESS_NOTES_API_REQUEST_PATH)
public class ProcessNotesControllerImpl implements ProcessNotesController {

    @Autowired
    private ProcessNotesService service;

    @GetMapping(path = Constants.EndPoints.PROCESS_NOTES_API_GET_BY_CLAIM_ID)
    @Override
    public ResponseEntity<List<ProcessNotesDTO>> getProcessNotes(@PathVariable String claimId) {
        return ResponseEntity.ok(service.getProcessNotesByClaimId(claimId));
    }

    @GetMapping(path = Constants.EndPoints.PROCESS_NOTES_API_HISTORY_DATA)
    @Override
    public ResponseEntity<String> addProcessNotesHistory(@RequestParam ExtensionStateCodeEnum status, @RequestParam String use, @RequestParam int count) {
        return ResponseEntity.ok(service.addHistoryData(status, use, count));
    }
}
