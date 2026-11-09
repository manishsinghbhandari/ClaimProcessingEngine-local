package io.coredge.health.claims.rest;

import static io.coredge.health.claims.constants.Constants.EndPoints.DOCUMENTS_API_PATH;
import static io.coredge.health.claims.constants.Constants.EndPoints.DOCUMENTS_CLAIMID_API_PATH;

import io.coredge.health.claims.models.DocumentsResponse;
import io.coredge.health.claims.service.DocumentsService;

import java.util.List;

import io.coredge.health.claims.worker.SendClaimForPaymentHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(DOCUMENTS_API_PATH)
@Slf4j
public class DocumentsController {

    @Autowired
    private DocumentsService documentsService;

    @Autowired
    private SendClaimForPaymentHandler sendClaimForPaymentHandler;

    @GetMapping(DOCUMENTS_CLAIMID_API_PATH)
    public ResponseEntity<List<DocumentsResponse>> getDocumentsByClaimID(@PathVariable String claimId) {
        return ResponseEntity.ok(documentsService.fetchDocumentsByClaimID(claimId));
    }

    @PostMapping
    public ResponseEntity<?> postDocumentsByClaimObject(@RequestBody String claimObject) {
        return documentsService.saveDocuments(claimObject);
    }
}
