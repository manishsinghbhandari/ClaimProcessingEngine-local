package io.coredge.health.claims.helpers;

import io.coredge.health.claims.exception.app.*;
import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.service.PostOfficeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

import static io.coredge.health.claims.exception.models.ErrorCode.GENDER_MISMATCH;
import static io.coredge.health.claims.exception.models.ErrorCode.MISSING_REFERENCE;

@Slf4j
@Component
public class PatientHelper {

    private final PostOfficeService postOfficeService;

    public PatientHelper(PostOfficeService postOfficeService) {
        this.postOfficeService = postOfficeService;
    }

    public Patient getPatient(Bundle bundle, Claim claim) {
        Patient patient = null;
        if (claim.hasPatient()) {
            String patientRef = claim.getPatient().getReference();
            patient =
                    (Patient)
                            postOfficeService.getResourceByUrl(bundle, patientRef, claim.getPatient().getId());

            if (!patient.hasGender() || Objects.isNull(patient.getGender())) {
                log.error(
                        "No Gender provided in claim:{} bundleId: {}", claim.getIdPart(), bundle.getIdPart());
                throw new BusinessException(GENDER_MISMATCH, "No gender specified for patient in Claim.");
            }
            String gender = patient.getGender().toCode();
            if (!"male".equalsIgnoreCase(gender) && !"female".equalsIgnoreCase(gender)) {
                log.error(
                        "Invalid gender '{}' provided in claim: {} bundleId: {}",
                        gender,
                        claim.getIdPart(),
                        bundle.getIdPart());
                throw new BusinessException(GENDER_MISMATCH, "Gender '" + gender + "' is not allowed for patient in Claim.");
            }
            claim.setPatient(new Reference("Patient/" + patient.getIdPart()));
            claim.addContained(patient);
        } else {
            throw new BusinessException(MISSING_REFERENCE, "No beneficiary patient resource found in the Bundle");
        }
        return patient;
    }


    public Patient extractPatient(Bundle bundle, Claim claim) {
        Patient patient = extractCoverageAndGetPatient(bundle);
        if (patient == null) {
            // Fallback: Use utility method to process patient from the claim
            return findPatient(bundle, claim);
        }
        return patient;
    }


    public Patient findPatient(Bundle bundle, Claim claim) {
        return Optional.ofNullable(claim)
                .filter(Claim::hasPatient)
                .map(Claim::getPatient)
                .map(patient -> postOfficeService.getResourceByUrl(bundle, patient.getReference(), patient.getId()))
                .map(Patient.class::cast)
                .orElse(null);
    }



    private Patient extractCoverageAndGetPatient(Bundle bundle) {
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            if (entry.getResource() instanceof Coverage coverage) {
                Reference beneficiaryReference = coverage.getBeneficiary();
                if (beneficiaryReference != null) {
                    return postOfficeService.getBeneficiaryPatient(bundle, beneficiaryReference.getReference());
                }
            }
        }
        return null; // Return null if no Coverage or Patient is found
    }

    /**
     * Validates gender and birthdate between HIE and Provider Patient.
     * @param providerPatient the Patient from the provider system
     * @param hiePatient the Patient from the HIE system
     */
    public void validatePatientData(Patient providerPatient, Patient hiePatient) {
        String hieId = hiePatient.getIdElement().getIdPart();

        validateHiePatientFields(hiePatient, hieId);

        String providerGender = getGender(providerPatient);
        String hieGender = getGender(hiePatient);

        if (!StringUtils.equalsIgnoreCase(providerGender, hieGender)) {
            log.warn("Mismatch in Gender between HIE and Provider. HIE Gender: {}, Provider Gender: {}, Patient ID: {}", hieGender, providerGender, hieId);
            throw new PatientGenderMismatchException(hieId, hieGender, providerGender);
        }

        Date providerBirthDate = providerPatient.getBirthDate();
        Date hieBirthDate = hiePatient.getBirthDate();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String hieDobStr = sdf.format(hiePatient.getBirthDate());
        String providerDobStr = sdf.format(providerPatient.getBirthDate());

        if (!Objects.equals(providerBirthDate, hieBirthDate)) {
            log.warn("Mismatch in Birth Date between HIE and Provider. HIE DOB: {}, Provider DOB: {}, Patient ID: {}", hieDobStr, providerDobStr, hieId);
            throw new PatientBirthDateMismatchException(hieId, hieDobStr, providerDobStr);
        }
    }

    public String validatePatientGender(String patientID ,String providedGender , Patient hiePatient ) {
        return validatePatientGender(patientID, providedGender, getGender(hiePatient));
    }

    public String validatePatientGender(String patientID , String providedGender , String hieGender) {

        if (StringUtils.isBlank(hieGender) || !(hieGender.equalsIgnoreCase("male") || hieGender.equalsIgnoreCase("female"))) {
            return String.format("Invalid Gender :%s in HIE Patient ID: %s", hieGender , patientID);
        }

        if (!StringUtils.equalsIgnoreCase(providedGender, hieGender)) {
            return String.format("Mismatch in Gender between HIE and Provider. HIE Gender: %s, Provider Gender: %s, Patient ID: %s", hieGender, providedGender , patientID);
        }
        return null;
    }

    public String validatePatientHouseHoldNumber(String patientID , String householdNumber , Patient hiePatient) {

        return validatePatientHouseHoldNumber(patientID , householdNumber , extractHouseholdFromPatient(hiePatient));
    }

    public String validatePatientHouseHoldNumber(String patientID , String householdNumber , String hieHouseHold) {

        if(StringUtils.isBlank(householdNumber) && StringUtils.isBlank(hieHouseHold)){
            return null;
        }

        if (StringUtils.isNotBlank(householdNumber) && StringUtils.isNotBlank(hieHouseHold)&& StringUtils.equals(householdNumber,hieHouseHold)) {
            return String.format("Household number Does not match in HIE Patient ID: %s", patientID);
        }

        if (StringUtils.isNotBlank(householdNumber) && StringUtils.isBlank(hieHouseHold)) {
            return String.format("Household number provided by provider is not found in HIE Patient ID: %s", patientID);
        }

        return null;
    }

    public String validatePatientBirthDate(String patientId , Date providerBirthDate, Date hieBirthDate) {
        Date today = new Date();

        // Calculate date 200 years ago
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(today);
        calendar.add(Calendar.YEAR, -200);
        Date twoHundredYearsAgo = calendar.getTime();

        if (hieBirthDate == null || hieBirthDate.after(today) || hieBirthDate.before(twoHundredYearsAgo)) {
            return String.format("Invalid Birth date: %s in HIE Patient with ID: %s. Birth date must be within the last 200 years and not in the future.", hieBirthDate , patientId);
        }

        if (!Objects.equals(providerBirthDate, hieBirthDate)) {
            return String.format("Mismatch in Birth Date between HIE and Provider. HIE DOB: %s, Provider DOB: %s, Patient in HIE Patient with ID: %s", hieBirthDate, providerBirthDate , patientId);
        }
        return null;
    }

    private void validateHiePatientFields(Patient hiePatient, String hieId) {
        String gender = getGender(hiePatient);
        if (StringUtils.isBlank(gender) || !(gender.equalsIgnoreCase("male") || gender.equalsIgnoreCase("female"))) {
            log.warn("Invalid Gender :{} in HIE Patient with ID: {}", gender, hieId);
            throw new PatientGenderInvalidException(gender, hieId);
        }

        Date birthDate = hiePatient.getBirthDate();
        Date today = new Date();

        // Calculate date 200 years ago
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(today);
        calendar.add(Calendar.YEAR, -200);
        Date twoHundredYearsAgo = calendar.getTime();

        if (birthDate == null || birthDate.after(today) || birthDate.before(twoHundredYearsAgo)) {
            log.warn("Invalid Birth date: {} in HIE Patient with ID: {}. Birth date must be within the last 200 years and not in the future.", birthDate, hieId);
            throw new PatientBirthDateInvalidException(birthDate, hieId);
        }
       String householdNumber = extractHouseholdFromPatient(hiePatient);
        log.info("Household number verify in HIE: {}", householdNumber);
        if (StringUtils.isBlank(householdNumber)) {
            throw new HouseholdNumberNotFoundException("Household number does not exist in HIE for Patient ID: %s".formatted(hieId));
        }

    }

    private static String getGender(Patient patient) {
        Enumerations.AdministrativeGender genderEnum = patient.getGender();
        return genderEnum != null ? genderEnum.toCode() : null;
    }


    public static String extractHouseholdFromPatient(Patient patient) {
        return patient.getIdentifier().stream()
                .filter(identifier -> identifier.hasType() &&
                        identifier.getType().hasCoding() &&
                        identifier.getType().getCoding().stream()
                                .anyMatch(coding -> "household-number".equals(coding.getCode())))
                .map(Identifier::getValue)
                .findFirst()
                .orElse(null);
    }


}
