package io.coredge.health.claims.service;

import ca.uhn.fhir.parser.IParser;
import io.coredge.health.claims.db.entity.Documents;
import io.coredge.health.claims.db.repositories.DocumentsRepository;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.app.DataNotFoundException;
import io.coredge.health.claims.exception.models.ErrorAttribute;
import io.coredge.health.claims.helpers.Common;
import io.coredge.health.claims.helpers.DocumentsHelper;
import io.coredge.health.claims.models.DocumentsResponse;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DocumentsService {

  @Autowired private DocumentsRepository documentsRepository;
  @Autowired private IParser fhirParser;

  @Autowired private DocumentsHelper documentsHelper;

  public List<DocumentsResponse> fetchDocumentsByClaimID(String claimId) {
    if (claimId == null || claimId.isEmpty()) {
      ErrorAttribute attribute =
          ErrorAttribute.builder().key("ClaimID").value("Claim id is null or empty").build();
      throw new BadRequestException(attribute);
    }

    Optional<List<Documents>> documentsList = documentsRepository.findByClaimId(claimId);
    if (documentsList.isEmpty()) {
      throw new DataNotFoundException("No document found for claim id: " + claimId);
    }

    return documentsList
        .map(
            list ->
                list.stream()
                    .map(DocumentsHelper::getDocumentsResponse)
                    .collect(Collectors.toList()))
        .orElseThrow(() -> new DataNotFoundException("No document found for claim id: " + claimId));
  }

  public ResponseEntity saveDocuments(String claimStr) {
    if (claimStr == null || claimStr.isEmpty()) {
      ErrorAttribute attribute =
          ErrorAttribute.builder()
              .key("ClaimBundle")
              .value("Claim bundle is null or empty")
              .build();
      throw new BadRequestException(attribute);
    }

    try {
      Bundle bundle = fhirParser.parseResource(Bundle.class, claimStr);

      Claim claimObj = Common.getClaimObj(bundle);
      if (claimObj == null) {
        return new ResponseEntity<>("No Claim obj found in bundle.", HttpStatus.BAD_REQUEST);
      }

      if (claimObj.hasSupportingInfo()) {
        List<Documents> listDocuments = documentsHelper.getDocumentsArr(claimObj);
        if (!listDocuments.isEmpty()) {
          List<Documents> listBlobDocuments = documentsHelper.uploadDocumentToBlob(listDocuments);

          if(!listBlobDocuments.isEmpty())
           documentsRepository.saveAll(listBlobDocuments);

          return new ResponseEntity<>("Data has been saved.", HttpStatus.CREATED);
        } else {
          return new ResponseEntity<>(
              "No valid document found to save.", HttpStatus.NOT_ACCEPTABLE);
        }
      } else {
        return new ResponseEntity<>("Claim contains no document.", HttpStatus.NOT_ACCEPTABLE);
      }
    } catch (Exception ex) {
      return new ResponseEntity<>("Invalid input JSON: " + ex.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

}
