package io.coredge.health.claims.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.app.DataValidationException;
import lombok.experimental.UtilityClass;
import org.hl7.fhir.r4.model.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@UtilityClass
public class FhirDocumentUtils {

    public void validateAttachments(List<Map<String, Object>> newAttachments) {
        List<String> requiredFields = List.of("contentType", "url", "title", "attachmentCode", "attachmentDisplay");
        newAttachments.forEach(attachment -> {
            requiredFields.forEach(field -> {
                if (attachment.get(field) == null || attachment.get(field).toString().isBlank()) {
                    throw new BadRequestException(String.format("Field '%s' is mandatory and cannot be null or blank.", field));
                }
            });
        });
    }

    public void validateRelatedInfo(ArrayNode relatedInfos) {
        List<String> requiredFields = List.of("claim", "relationship");
        relatedInfos.forEach(relatedInfo -> requiredFields.forEach(field -> {
            if (relatedInfo.get(field) == null || relatedInfo.get(field).toString().isBlank()) {
                throw new BadRequestException(String.format("Field '%s' is mandatory and cannot be null or blank.", field));
            }
        }));
    }

    public Boolean validateStateCode(DomainResource resource, String fhirServerBaseUrl) {
        String extnUrl = fhirServerBaseUrl + "/StructureDefinition/claim-state-extension";

        String[] stateCodeAllowForDocUpdate = {"in-review", "clinical-review", "sent-back"};

        // Find the extension with the specified URL
        Optional<Extension> extensionOpt = resource.getExtension().stream()
                .filter(ext -> extnUrl.equals(ext.getUrl())).findFirst();

        if (extensionOpt.isPresent()) {
            Extension stateExtension = extensionOpt.get();
            if (stateExtension.hasValue() && stateExtension.getValue() instanceof CodeableConcept concept) {
                if (concept.hasCoding() && concept.getCodingFirstRep().hasCode()) {
                    String code = concept.getCodingFirstRep().getCode();
                    // Validate the code against allowed values
                    if (Arrays.asList(stateCodeAllowForDocUpdate).contains(code)) {
                        return true;
                    } else {
                        throw new DataValidationException("Invalid status, Updating of doc only allowed for claim/preauthorization having status code: " + Arrays.toString(stateCodeAllowForDocUpdate));
                    }
                }
            }
        }

        throw new DataValidationException("Claim status code not found or extension missing.");
    }

    public Attachment createAttachment(Map<String, Object> newAttachment, String systemUrl) {
        Attachment attachment = new Attachment();
        attachment.setContentType((String) newAttachment.get("contentType"));
        attachment.setUrl((String) newAttachment.get("url"));
        attachment.setTitle((String) newAttachment.get("title"));

        String attachmentCode = (String) newAttachment.get("attachmentCode");
        String attachmentDisplay = (String) newAttachment.get("attachmentDisplay");
        if (attachmentCode != null) {
            Extension extension = new Extension(systemUrl, new CodeableConcept().addCoding(new Coding(systemUrl, attachmentCode, attachmentDisplay)));
            attachment.addExtension(extension);
        }
        return attachment;
    }

    public Claim.SupportingInformationComponent createSupportingInfo(int sequence, Attachment attachment) {
        return new Claim.SupportingInformationComponent().setSequence(sequence).setCategory(new CodeableConcept().addCoding(new Coding("http://terminology.hl7.org/CodeSystem/claiminformationcategory", "attachment", "Attachment"))).setValue(attachment);
    }

    public Claim.RelatedClaimComponent createRelatedComponent(JsonNode relatedInfo) {
        String systemUrl = relatedInfo.at("/claim/identifier/system").asText();
        String value = relatedInfo.at("/claim/identifier/value").asText();
        Identifier identifier = new Identifier().setSystem(systemUrl).setValue(value);
        Reference reference = new Reference().setIdentifier(identifier);

        CodeableConcept relationship = new CodeableConcept();
        ArrayNode codings = relatedInfo.withArray("/relationship/coding");
        codings.forEach(coding -> {
            String system = coding.at("/system").asText();
            String code = coding.at("/code").asText();
                    relationship.addCoding( new Coding().setSystem(system).setCode(code));
        });
        return new Claim.RelatedClaimComponent().setClaim(reference).setRelationship(relationship);
    }
}
