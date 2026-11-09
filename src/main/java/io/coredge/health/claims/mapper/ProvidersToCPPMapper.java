package io.coredge.health.claims.mapper;

import io.coredge.health.claims.db.entity.Provider;
import io.coredge.health.claims.models.cpp.RequestCppProviders;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ProvidersToCPPMapper {

    public RequestCppProviders convertFromProvidersToCPP(Provider provider) {
       return RequestCppProviders.builder()
               .providerName(provider.getName())
               .providerType(provider.getFacilityType())
               .level(provider.getLevel())
               .registrationNumber(provider.getRegNo())
               .sladeCode(null)
               .frCode(provider.getFidCode())
               .county(provider.getCounty())
               .countyCode(provider.getCountyCode())
               .facilityOwnership(provider.getFacilityAgent())
               .contactNumber(provider.getPhone())
               .contactPerson(provider.getContactPerson())
               .emailId(provider.getEmail())
               .providerAddress(provider.getPostalAddress())
               .build();
    }
}
