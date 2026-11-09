package io.coredge.health.claims.helpers;

import io.coredge.health.claims.db.entity.Documents;
import io.coredge.health.claims.models.DocumentsResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r4.model.*;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DocumentsHelper {

    private final BlobUploadHandler blobUploadHandler;

    public DocumentsHelper(BlobUploadHandler blobUploadHandler) {
        this.blobUploadHandler = blobUploadHandler;
    }

    // doc response class
    public static DocumentsResponse getDocumentsResponse(Documents documents) {
        DocumentsResponse response = new DocumentsResponse();
        BeanUtils.copyProperties(documents, response);
        return response;
    }

    // getting document arr from document object
    public List<Documents> getDocumentsArr(Claim claimObj) {
        List<Documents> listDocuments = new ArrayList<>();

        if (claimObj != null && claimObj.hasSupportingInfo()) {
            for (Claim.SupportingInformationComponent suppInfo : claimObj.getSupportingInfo()) {
                if (suppInfo.hasValueAttachment()) {
                    Attachment attachment = suppInfo.getValueAttachment();

                    Documents doc = new Documents();
                    doc.setClaimId(claimObj.getIdPart());

                    if (attachment.hasTitle()) {
                        doc.setTitle(attachment.getTitle());
                    }

                    if (attachment.hasExtension()) {
                        for (Extension ext : attachment.getExtension()) {
                            if (ext.getValue() instanceof CodeableConcept valueCodeableConcept) {
                                if (valueCodeableConcept.hasCoding()) {
                                    for (Coding coding : valueCodeableConcept.getCoding()) {
                                        if (coding.hasDisplay()) {
                                            doc.setDocType(coding.getDisplay());
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (attachment.hasUrl()) {
                        doc.setUrlSp(attachment.getUrl());
                    }
                    if (attachment.hasUrl()) {
                        doc.setUrlSha("NA");
                    }
                    listDocuments.add(doc);
                }
            }
        }

        return listDocuments;
    }

    // for downloading and uploading the document which is in supportingInfo key
    public List<Documents> uploadDocumentToBlob(List<Documents> lst) throws IOException {
        List<Documents> listDocuments = new ArrayList<>();
        for (Documents dc : lst) {
            String blobURL = blobUploadHandler.uploadToBlobStorage(dc.getUrlSp(), dc.getClaimId() + "/" + dc.getDocType());
            if (StringUtils.hasText(blobURL))
                dc.setUrlSha(blobURL);
            listDocuments.add(dc);
        }
        return listDocuments;
    }

}
