package io.coredge.health.claims.utils;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.app.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ca.uhn.fhir.rest.gclient.TokenClientParam;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class FhirHelper {

    @Autowired
    private IGenericClient fhirClient;

    @Value("${TERMINOLOGY_SERVER_BASE_URL}")
    private static String terminologyBaseUrl;

    private static final ZoneId NAIROBI_ZONE = ZoneId.of("Africa/Nairobi");

    private static final DateTimeFormatter UTC_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    public FhirHelper() {

    }

    public static int calculateAge(Date birthdate) {
        LocalDate localBirthDate = convertToLocalDate(birthdate);
        // Get the current date
        LocalDate currentDate = LocalDate.now();
        // Calculate the period between the birthdate and the current date
        return Period.between(localBirthDate, currentDate).getYears();
    }

    public static LocalDate convertToLocalDate(Date date) {
        // Convert Date to Instant, then to LocalDate using system's default zone
        return Instant.ofEpochMilli(date.getTime()).atZone(NAIROBI_ZONE).toLocalDate();
    }

    /**
     * Calculate length of stay for this claim.
     *
     * @param claim
     * @return length of stay for this claim
     */
    public static int calculateLoS(Claim claim) {
        LocalDate localStartDate = convertToLocalDate(claim.getBillablePeriod().getStart());
        LocalDate localEndDate = convertToLocalDate(claim.getBillablePeriod().getEnd());
        return calculateLoS(localStartDate, localEndDate);
    }

    public static int calculateLoS(Date startDate, Date endDate) {
        LocalDate localStartDate = convertToLocalDate(startDate);
        LocalDate localEndDate = convertToLocalDate(endDate);
        return calculateLoS(localStartDate, localEndDate);
    }

    public static int calculateLoS(LocalDate startDate, LocalDate endDate) {
        long los = ChronoUnit.DAYS.between(startDate, endDate);

        if (los == 0) {
            los = 1;
        }
        return (int) los;
    }

    public static Optional<Identifier> findIdentifierBySystemAndCode(Patient patient, String system, String value) {
        return patient.getIdentifier().stream()
                .filter(identifier -> system.equals(identifier.getSystem()) && value.equals(identifier.getValue()))
                .findFirst();
    }

    public static Optional<Identifier> findIdentifierBySystemAndCode(Organization organization, String system, String value) {
        return organization.getIdentifier().stream()
                .filter(identifier -> system.equals(identifier.getSystem()) && value.equals(identifier.getValue()))
                .findFirst();
    }

    public static Optional<Identifier> findIdentifierBySystemAndCode(Practitioner practitioner, String system, String value) {
        return practitioner.getIdentifier().stream()
                .filter(identifier -> system.equals(identifier.getSystem()) && value.equals(identifier.getValue()))
                .findFirst();
    }

    public DomainResource getResourceByIdentifierValue(DomainResource resource, String identifierValue) {
        Bundle results = fhirClient.search()
                .forResource(resource.getClass())
                .where(new TokenClientParam("identifier")
                        .exactly()
                        .code(identifierValue))
                .returnBundle(Bundle.class)
                .execute();

        // Check if we have results
        if (!results.getEntry().isEmpty()) {
            return (DomainResource) results.getEntry().get(0).getResource();
        }
        return null;
    }

    public static LocalDateTime convertToLocalDateTime(Date date) {
        return date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    public static LocalDate convertToLocalDateFromDateTime(Date date) {
        LocalDateTime startDate = date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        return startDate.toLocalDate();
    }

    public boolean isClaimResponseExistsInFhir(String claimId) {
        Bundle results = fhirClient
                .search()
                .forResource(Claim.class)
                .where(Claim.IDENTIFIER.exactly().identifier(claimId))
                .count(1) // Limit to 1 result to check existence only
                .returnBundle(Bundle.class)
                .execute();
        return results.getTotal() > 0;
    }

    public void deleteObjInFhir(String resourceType, String id) {
        try {
            fhirClient.delete().resourceById(resourceType, id).execute();
            log.info("Successfully deleted {} with ID: {}", resourceType, id);
        } catch (Exception e) {
            log.error("Failed to delete {} with ID: {}. Error: {}", resourceType, id, e.getMessage());
        }
    }

    public boolean isResourceExistsInFhir(Class<? extends Resource> resourceClass, String identifier) {
        Bundle results = fhirClient
                .search()
                .forResource(resourceClass)
                .where(Resource.RES_ID.exactly().identifier(identifier))
                .count(1) // Limit to 1 result to check existence only
                .returnBundle(Bundle.class)
                .execute();

        return results != null && results.hasEntry();
    }

    // Usage:
    public boolean isPatientExistsInFhir(String patientRef) {
        return isResourceExistsInFhir(Patient.class, patientRef);
    }

    public boolean isPractitionerExistsInFhir(String practitionerRegNo) {
        return isResourceExistsInFhir(Practitioner.class, practitionerRegNo);
    }

    public boolean isProviderExistsInFhir(String providerCode) {
        return isResourceExistsInFhir(Organization.class, providerCode);
    }


    public static Date convertStringToDate(String dateStr) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        try {
            return sdf.parse(dateStr);
        } catch (ParseException e) {
            log.error("Exp {}", e.toString());
            return null; // Return null or handle appropriately if parsing fails
        }
    }

    public static String convertToStringFromDateTime(LocalDateTime date) {
        if (date == null) {
            return "";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        // Set time to 00:00:00 and convert to UTC before formatting
        return date.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC)
                .format(formatter);
    }

    public static LocalDateTime convertToDateTimeFromString(String dateTime) {
        if (dateTime == null || dateTime.isBlank()) {
            return null;
        }

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                        .withZone(ZoneOffset.UTC);

        return LocalDateTime.parse(dateTime, formatter);
    }


    public static String convertToStringFromDate(LocalDate date) {
        if (date == null) {
            return "";
        }

        return date.atStartOfDay(ZoneOffset.UTC).format(UTC_DATE_TIME_FORMATTER);
    }

    public static String convertDateStringToTargetFormat(Date date) {
        if (date == null) {
            return "";
        }
        // Convert Date to LocalDate and set time to 00:00:00
        LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

        // Define the output formatter
        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

        // Format the LocalDate to the target format, ensuring time is 00:00:00
        return localDate.atStartOfDay(ZoneOffset.UTC).format(outputFormatter);
    }

    /**
     * Fetches a FHIR {@link Bundle} resource from the FHIR server using the provided bundle ID.
     * <p>
     * This method uses the configured {@code fhirClient} to read the Bundle resource. If the Bundle
     * with the given ID does not exist, a {@link BadRequestException} is thrown with an appropriate message.
     * </p>
     *
     * <p><b>Use Case:</b> Part of IPS (International Patient Summary) related functionality.<br>
     * <b>Reference:</b> EPORTAL-397</p>
     *
     * @param bundleId the unique ID of the FHIR Bundle to retrieve
     * @return the fetched {@link Bundle} resource
     * @throws BadRequestException if the bundle is not found on the FHIR server
     */
    public Bundle getBundleObject(String bundleId) {
        log.info("Fetching bundle for bundleId: {}", bundleId);
        Bundle bundle;
        try {
            bundle = fhirClient.read().resource(Bundle.class).withId(bundleId).execute();
        } catch (ResourceNotFoundException e) {
            throw new BadRequestException("Bundle not found for ID: " + bundleId, e);
        }
        return bundle;
    }

    public static String extractPatientId(Claim claim) {
        String patientRef = claim.getPatient().getReference();
        if (patientRef != null && patientRef.contains("/")) {
            return patientRef.substring(patientRef.lastIndexOf('/') + 1);
        }
        return patientRef; // return original if no slash found
    }

    public static String extractDoctorIdentifier(Claim claim, IGenericClient fhirClient) {

        if (claim == null || !claim.hasCareTeam()) {
            return null;
        }

        Reference practitionerRef = claim.getCareTeamFirstRep().getProvider();

        if (practitionerRef == null || !practitionerRef.hasReference()) {
            return null;
        }

        String practitionerId = practitionerRef.getReferenceElement().getIdPart();
        if (practitionerId == null) {
            return null;
        }

        Practitioner practitioner;
        try {
            practitioner = fhirClient.read()
                    .resource(Practitioner.class)
                    .withId(practitionerId)
                    .execute();
        } catch (Exception e) {
            return null;
        }

        if (!practitioner.hasIdentifier()) {
            return null;
        }

        // First try to get National_ID
        for (Identifier identifier : practitioner.getIdentifier()) {
            if (identifier.hasSystem() && identifier.hasValue()) {
                if (identifier.getSystem().contains("National_ID")) {
                    return identifier.getValue();
                }
            }
        }

        // Fallback to any identifier value
        for (Identifier identifier : practitioner.getIdentifier()) {
            if (identifier.hasValue()) {
                return identifier.getValue();
            }
        }
        return null;
    }

    // Helper method to get null property names
    public static String[] getNullPropertyNames(Object source) {
        final org.springframework.beans.BeanWrapper src = new org.springframework.beans.BeanWrapperImpl(source);
        java.beans.PropertyDescriptor[] pds = src.getPropertyDescriptors();

        Set<String> emptyNames = new HashSet<>();
        for (java.beans.PropertyDescriptor pd : pds) {
            Object srcValue = src.getPropertyValue(pd.getName());
            if (srcValue == null) emptyNames.add(pd.getName());
        }
        return emptyNames.toArray(new String[0]);
    }

}
