package io.coredge.health.claims.helpers;

import io.coredge.health.claims.db.entity.Diagnosis;
import io.coredge.health.claims.models.DiagnosisResponse;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.springframework.beans.BeanUtils;

@UtilityClass
public class DiagnosisHelper {

  // Private helper functions
  public Claim getClaimObj(Bundle bundle) {
    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      if (entry.getResource() instanceof Claim) {
        return (Claim) entry.getResource();
      }
    }
    return null;
  }

  public List<Diagnosis> getDiagnosisArr(Claim claimObj) {
    List<Diagnosis> listDiagnosis = new ArrayList<>();
    for (Claim.DiagnosisComponent diagnosis : claimObj.getDiagnosis()) {
      if (diagnosis.hasDiagnosisCodeableConcept()
          && diagnosis.getDiagnosisCodeableConcept().hasCoding()) {
        for (var coding : diagnosis.getDiagnosisCodeableConcept().getCoding()) {
          Diagnosis dia = new Diagnosis();
          dia.setCode(coding.getCode());
          dia.setName(coding.getDisplay());
          dia.setClaimId(claimObj.getIdPart());
          dia.setPatientId(getPatientIdToURL(claimObj));
          dia.setStatus("Active");
          listDiagnosis.add(dia);
        }
      }
    }
    return listDiagnosis;
  }

  public String getPatientIdToURL(Claim claimObj) {
    if(claimObj.getPatient().hasReference()) {
      String patientRef = claimObj.getPatient().getReference();
      return Common.getLastPartToURL(patientRef);
    }
    return "NA";
  }

  public static DiagnosisResponse getDiagnosisResponse(Diagnosis diagnosis) {
    DiagnosisResponse response = new DiagnosisResponse();
    BeanUtils.copyProperties(diagnosis, response);
    response.setCreatedBy(diagnosis.getCreatedBy());
    return response;
  }
}
