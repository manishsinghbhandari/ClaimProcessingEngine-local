package io.coredge.health.claims.helpers;

import lombok.experimental.UtilityClass;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;

@UtilityClass
public class Common {

  // for getting the claim object from bundle
  public Claim getClaimObj(Bundle bundle) {
    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      if (entry.getResource() instanceof Claim) {
        return (Claim) entry.getResource();
      }
    }
    return null;
  }

  // for getting last part of URL
  public String getLastPartToURL(String urlStr) {
    return urlStr.substring(urlStr.lastIndexOf("/") + 1);
  }
}
