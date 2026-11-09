package io.coredge.health.claims.models.benefits;

public interface BenefitsMasterProjection {
    String getProviderPaymentMechanism();
    String getLimitsIndividualHousehold();
    String getInterventionQuantityYear();
    String getAppliesToSchemes();
    String getBedType();
    String getAccessCode();
    String getGender();
    Boolean getNeedsPreAuth();
}
