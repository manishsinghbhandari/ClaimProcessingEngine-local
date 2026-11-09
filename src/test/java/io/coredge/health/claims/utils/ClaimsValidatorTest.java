package io.coredge.health.claims.utils;

import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.entity.Member;
import io.coredge.health.claims.db.entity.Provider;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.LineItemRepository;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.helpers.PatientHelper;
import io.coredge.health.claims.service.ClaimRequestProcessor;
import io.coredge.health.claims.service.PostOfficeService;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static io.coredge.health.claims.exception.models.ErrorCode.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static io.coredge.health.claims.constants.Constants.Use.PREAUTHORIZATION;
import static io.coredge.health.claims.constants.Constants.Use.CLAIM;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)


class ClaimsValidatorTest {

    @InjectMocks
    private ClaimsValidator claimsValidator;

    @Mock
    ClaimRequestProcessor claimRequestProcessor;

    @Mock
    private InsuranceClaimsRepository insuranceClaimsRepository;

    @Mock
    private LineItemRepository lineItemRepository;

    @Mock
    private PatientHelper patientHelper;

    @Mock
    PostOfficeService postOfficeService ;


    private static final String PRE_AUTH_NOT_IN_APPROVE_STATE = "Pre-Auth is not in APPROVED state.";
    private static final String PRE_AUTH_ALREADY_EXISTS = "Pre-Auth already attached on this claim: ";
    

    @Test
    void shouldReturnPreAuthId_whenRelatedClaimHasPreAuthRelationship() {
        // Given
        Claim claim = new Claim();

        // Create "pre-auth" relationship
        Coding coding = new Coding();
        coding.setCode("pre-auth");

        CodeableConcept relationship = new CodeableConcept();
        relationship.addCoding(coding);

        // Create related claim with identifier
        Claim.RelatedClaimComponent relatedClaim = new Claim.RelatedClaimComponent();
        relatedClaim.setRelationship(relationship);

        Reference claimReference = new Reference();
        Identifier identifier = new Identifier();
        identifier.setValue("12345-PA");
        claimReference.setIdentifier(identifier);
        relatedClaim.setClaim(claimReference);

        // Add related claim to claim object
        claim.setRelated(List.of(relatedClaim));

        // When
        String preAuthId = claim.getRelated().stream()
                .filter(related -> Optional.ofNullable(related.getRelationship())
                        .map(CodeableConcept::getCodingFirstRep)
                        .map(Coding::getCode)
                        .filter("pre-auth"::equalsIgnoreCase)
                        .isPresent())
                .map(rel -> rel.getClaim().getIdentifier().getValue())
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElseThrow(() -> new BusinessException(PRE_AUTH_NOT_EXISTS, "The Pre-Auth ID must not be blank in the bundle."));

        // Then
        assertEquals("12345-PA", preAuthId);
    }

    @Test
    void shouldThrowException_whenPreAuthClaimDoesNotExist() {
        // Given
        Claim claim = new Claim();
        claim.setRelated(List.of()); // No related claims

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            claim.getRelated().stream()
                    .filter(related -> Optional.ofNullable(related.getRelationship())
                            .map(CodeableConcept::getCodingFirstRep)
                            .map(Coding::getCode)
                            .filter("pre-auth"::equalsIgnoreCase)
                            .isPresent())
                    .map(rel -> rel.getClaim().getIdentifier().getValue())
                    .filter(StringUtils::isNotBlank)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(PRE_AUTH_NOT_EXISTS, "The Pre-Auth ID must not be blank in the bundle."));
        });

        assertEquals("The Pre-Auth ID must not be blank in the bundle.", exception.getMessage());
    }

    private @NotNull Claim findClaim() {
        Claim claim = new Claim();
        claim.setUse(Claim.Use.CLAIM);
        return claim;
    }

    @Test
    void shouldThrowOnlyPreAuthNotExistsException() {
        // Given: No pre-auth claims exist
        String preAuthId = "c6bf4c09-8b0d-457d-a6d8-c644dddb39a5";
        UUID preAuthUUID = UUID.fromString(preAuthId);

        final Claim claim = new Claim(); // Create a new Claim instance
        Bundle bundle = new Bundle();

        // Mock repository returning empty optional (no pre-auth exists)
        when(insuranceClaimsRepository.getClaimIdAndUse(preAuthUUID, PREAUTHORIZATION))
                .thenReturn(Optional.empty()); // No pre-auth in DB

        // Then: Expect "Pre-auth ID does not exist" exception
        assertThatThrownBy(() -> claimsValidator.validatePreAuthApproved(preAuthId, claim, bundle))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Pre-auth ID does not exist in DB");
    }

    @Test
    void shouldThrowOnlyPreAuthNotApprovedException() {
        // Given: Pre-auth exists but is not APPROVED
        String preAuthId = "c6bf4c09-8b0d-457d-a6d8-c644dddb39a5";
        UUID preAuthUUID = UUID.fromString(preAuthId);

        // Create a non-approved pre-auth claim
        InsuranceClaim preAuthClaim = new InsuranceClaim();
        preAuthClaim.setClaimId(preAuthId);
        preAuthClaim.setStatus(ExtensionStateCodeEnum.PENDING); // Not APPROVED

        Optional<InsuranceClaim> mockOptionalClaims = Optional.of((preAuthClaim));

        final Claim claim = findClaim();
        Bundle bundle = new Bundle();

        // Mock repository to return the pre-auth claim
        when(insuranceClaimsRepository.getClaimIdAndUse(preAuthUUID, PREAUTHORIZATION))
                .thenReturn(mockOptionalClaims);


        // Then: Expect only "Pre Auth Id is not in APPROVED state" exception
        assertThatThrownBy(() -> claimsValidator.validatePreAuthApproved(preAuthId, claim, bundle))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(PRE_AUTH_NOT_IN_APPROVE_STATE);
    }

    @Test
    void shouldThrowOnlyPreAuthAlreadyExistsException() {
        // Given: Pre-auth exists and is already claimed
        String preAuthId = "c6bf4c09-8b0d-457d-a6d8-c644dddb39a5";
        UUID preAuthUUID = UUID.fromString(preAuthId);

        // Create an approved pre-auth claim
        InsuranceClaim preAuthClaim = new InsuranceClaim();
        Bundle bundle = new Bundle();

        preAuthClaim.setClaimId(preAuthId);
        preAuthClaim.setStatus(ExtensionStateCodeEnum.APPROVED);

        final Claim claim = findClaim();

        // Mock preAuthExists to simulate that the pre-auth already exists
        when(insuranceClaimsRepository.findByPreAuthIdAndUse(preAuthUUID, CLAIM))
                .thenReturn(Optional.of(preAuthClaim));

        // Then: Expect only "Pre Auth already exists" exception
        assertThatThrownBy(() -> claimsValidator.validatePreAuthApproved(preAuthId, claim, bundle))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(PRE_AUTH_ALREADY_EXISTS + preAuthClaim.getClaimId());
    }





    private record getDetails(List<Claim.ItemComponent> listOfComponent, Claim.ItemComponent itemComponent) {
    }


    private record Result(Bundle bundle, Organization organization, List<Claim.ItemComponent> listOfComponent, Claim.ItemComponent itemComponent) {
    }


    // Validate Provider

    @Test
    void validatePreAuthProviderTest() {
        Claim claim = new Claim();
        Reference ref = new Reference();
        ref.setReference("https://mis.apeiro-digital.com/fhir/Organization/Failed");
        claim.setProvider(ref);

        Bundle bundle = new Bundle();
        List<Bundle.BundleEntryComponent> listOfEntryComponents = new ArrayList<>();
        Bundle.BundleEntryComponent bundleEntryComponent = new Bundle.BundleEntryComponent();
        Coverage coverage = new Coverage();
        Organization organization = new Organization();
        organization.setId("20332");

        Reference referenceBeneficiary = new Reference();
        referenceBeneficiary.setId("CR0087686760183");
        coverage.setBeneficiary(referenceBeneficiary);
        referenceBeneficiary.setReference("https://mis.apeiro-digital.com/fhir/Coverage/CR0087686760183-1-sha-coverage");
        bundleEntryComponent.setResource(coverage);
        listOfEntryComponents.add(bundleEntryComponent);
        bundle.setEntry(listOfEntryComponents);
        List<Claim.ItemComponent> listOfComponent = new ArrayList<>();
        Claim.ItemComponent itemComponent = new Claim.ItemComponent();

        claim.setProvider(referenceBeneficiary);
        getDetails result = new getDetails(listOfComponent, itemComponent);
        Result bundleDetails = new Result(bundle, organization, result.listOfComponent(), result.itemComponent());

        when(postOfficeService.getResourceByUrl(any(Bundle.class), anyString(), any()))
                .thenReturn(bundleDetails.organization());

        InsuranceClaim insuranceClaim = new InsuranceClaim();
        // Then: Expect only "Pre Auth already exists" exception
        assertThatThrownBy(() -> claimsValidator.validatePreAuthProvider(claim, insuranceClaim, bundle))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Mismatch detected: The Organization ID in the bundle does not contain an MFL or a FID code.");

    }

    @Test
    void validatePreAuthProviderMFLTest() {
        Claim claim = new Claim();
        Reference ref = new Reference();
        ref.setId("MFL-28960");
        claim.setProvider(ref);

        InsuranceClaim insuranceClaim = new InsuranceClaim();
        Provider provider = new Provider();
        provider.setMflCode("MFL-20339");
        insuranceClaim.setProvider(provider);
        Bundle bundle = new Bundle();
        List<Bundle.BundleEntryComponent> listOfEntryComponents = new ArrayList<>();
        Bundle.BundleEntryComponent bundleEntryComponent = new Bundle.BundleEntryComponent();
        Coverage coverage = new Coverage();
        Organization organization = new Organization();
        organization.setId("MFL-20332");

        Reference referenceBeneficiary = new Reference();
        referenceBeneficiary.setId("CR0087686760183");
        coverage.setBeneficiary(referenceBeneficiary);
        referenceBeneficiary.setReference("https://mis.apeiro-digital.com/fhir/Coverage/CR0087686760183-1-sha-coverage");
        bundleEntryComponent.setResource(coverage);
        listOfEntryComponents.add(bundleEntryComponent);
        bundle.setEntry(listOfEntryComponents);
        List<Claim.ItemComponent> listOfComponent = new ArrayList<>();
        Claim.ItemComponent itemComponent = new Claim.ItemComponent();

        claim.setProvider(referenceBeneficiary);
        getDetails result = new getDetails(listOfComponent, itemComponent);
        Result bundleDetails = new Result(bundle, organization, result.listOfComponent(), result.itemComponent());

        when(postOfficeService.getResourceByUrl(any(Bundle.class), anyString(), any()))
                .thenReturn(bundleDetails.organization());

        // Then: Expect BusinessException with specific FID mismatch message
        assertThatThrownBy(() -> claimsValidator.validatePreAuthProvider(claim, insuranceClaim, bundle))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MFL Code mismatch:")
                .hasMessageContaining("FHIR Bundle MFL-20332")
                .hasMessageContaining("does not match with PreAuth MFL-20339.");
    }


    @Test
    void validatePreAuthProviderFIDTest() {
        Claim claim = new Claim();
        Reference ref = new Reference();
        ref.setId("FID-28960");
        claim.setProvider(ref);

        InsuranceClaim insuranceClaim = new InsuranceClaim();
        Provider provider = new Provider();
        provider.setFidCode("FID-20339");
        insuranceClaim.setProvider(provider);
        Bundle bundle = new Bundle();
        List<Bundle.BundleEntryComponent> listOfEntryComponents = new ArrayList<>();
        Bundle.BundleEntryComponent bundleEntryComponent = new Bundle.BundleEntryComponent();
        Coverage coverage = new Coverage();
        Organization organization = new Organization();
        organization.setId("FID-20332");

        Reference referenceBeneficiary = new Reference();
        referenceBeneficiary.setId("CR0087686760183");
        coverage.setBeneficiary(referenceBeneficiary);
        referenceBeneficiary.setReference("https://mis.apeiro-digital.com/fhir/Coverage/CR0087686760183-1-sha-coverage");
        bundleEntryComponent.setResource(coverage);
        listOfEntryComponents.add(bundleEntryComponent);
        bundle.setEntry(listOfEntryComponents);
        List<Claim.ItemComponent> listOfComponent = new ArrayList<>();
        Claim.ItemComponent itemComponent = new Claim.ItemComponent();

        claim.setProvider(referenceBeneficiary);
        getDetails result = new getDetails(listOfComponent, itemComponent);
        Result bundleDetails = new Result(bundle, organization, result.listOfComponent(), result.itemComponent());

        when(postOfficeService.getResourceByUrl(any(Bundle.class), anyString(), any()))
                .thenReturn(bundleDetails.organization());

        // Then: Expect BusinessException with specific FID mismatch message
        // Then: Expect BusinessException with specific FID mismatch message
        assertThatThrownBy(() -> claimsValidator.validatePreAuthProvider(claim, insuranceClaim,bundle))
                .isInstanceOf(BusinessException.class)

                .hasMessageContaining("FID Code mismatch:")
                .hasMessageContaining("FHIR Bundle FID-20332")
                .hasMessageContaining("does not match with PreAuth FID-20339.");
    }


    @Test
    void shouldThrowExceptionWhenProviderDoesNotExist() {
        // Given: Claim with no provider set (null provider)
        Claim claim = new Claim();
        InsuranceClaim insuranceClaim = new InsuranceClaim();
        // Then: Expect BusinessException for missing provider
        Bundle bundle = new Bundle();

        assertThatThrownBy(() -> claimsValidator.validatePreAuthProvider(claim, insuranceClaim, bundle))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Provider reference does not exist in the bundle.");
    }

    // Patient

    @Test
    void shouldThrowExceptionWhenPatientGenderValueBlank() {
        // Given: InsuranceClaim with a patient that has gender "female"
        InsuranceClaim insuranceClaim = new InsuranceClaim();
        Member member = new Member();
        member.setCrId("CR6156621816927");
        member.setGender("female");
        insuranceClaim.setPatient(member);

        // Given: Claim with patient reference
        Claim claim = new Claim();
        Reference reference = new Reference("Patient/CR6156621816927");
        claim.setPatient(reference);

        // Given: Bundle and Patient (Patient gender is missing/blank to trigger exception)
        Patient patient = new Patient();
        patient.setId("CR6156621816927");
        patient.setGender(null);
        Bundle bundle = new Bundle();

        // Mock `PatientUtils.getPatient`
        when(patientHelper.extractPatient(bundle, claim)).thenReturn(patient);

        // Then: Expect BusinessException due to missing gender
        assertThatThrownBy(() -> claimsValidator.validatePreAuthClaimPatient(claim, insuranceClaim, bundle))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Gender field does not exist in the bundle");
    }

    @Test
    void shouldThrowExceptionWhenPatientIdAndPreAuthIdDoesNotMatch() {
        // Given: Pre-auth claim with a different patient ID
        InsuranceClaim insuranceClaim = new InsuranceClaim();
        Member member = new Member();
        member.setCrId("CR6156621816928"); // Pre-auth claim patient ID
        insuranceClaim.setPatient(member);

        // Given: Patient with a different ID
        Patient patient = new Patient();
        patient.setId("CR6156621816927"); // Different patient ID

        // Expected error message
        String expectedMessage = String.format(
                "Patient Id of Claim %s and Patient Id of Pre Auth %s do not match",
                patient.getIdElement().getIdPart(), insuranceClaim.getPatient().getCrId()
        );

        // Then: Expect BusinessException due to patient ID mismatch
        assertThatThrownBy(() -> claimsValidator.patientVerifyFromBundle(insuranceClaim, patient))
                .isInstanceOf(BusinessException.class)
                .hasMessage(expectedMessage);
    }

    @Test
    void shouldThrowExceptionWhenPatientIdIsBlank() {
        // Given: Pre-auth claim with a valid patient
        InsuranceClaim insuranceClaim = new InsuranceClaim();
        Member member = new Member();
        member.setCrId("CR6156621816927"); // Valid CR ID
        insuranceClaim.setPatient(member);

        // Given: Patient with a blank ID
        Patient patient = new Patient();
        patient.setId(""); // Blank ID (edge case)

        // Then: Expect BusinessException due to blank patient ID
        assertThatThrownBy(() -> claimsValidator.patientVerifyFromBundle(insuranceClaim, patient))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Patient ID must not be blank"); // Exact message check
    }



    @Test
    void shouldThrowExceptionWhenHasPatientDoesNotExist() {

        // Given: Claim with no provider set (null provider)
        InsuranceClaim insuranceClaim = new InsuranceClaim();
        Claim claim = new Claim();
        Bundle bundle = new Bundle();

        // Then: Expect BusinessException for missing provider
        assertThatThrownBy(() -> claimsValidator.validatePreAuthClaimPatient(claim, insuranceClaim, bundle))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Missing patient reference in claim");
    }


    @Test
    void shouldSkipValidationWhenPreAuthIdIsBlank() {
        // Given: A claim with "CLAIM" use
        Claim claim = new Claim();
        claim.setUse(Claim.Use.CLAIM);
        Bundle bundle = new Bundle();

        // Mocking fetchPreAuthIdFromBundle to return a blank string
        when(claimRequestProcessor.fetchPreAuthIdFromBundle(claim)).thenReturn("");

        // When: validatePreAuthClaims is called
        claimsValidator.validatePreAuthClaims(claim, bundle);

        // Then: validatePreAuthApproved should NOT be called
        verify(claimRequestProcessor, times(1)).fetchPreAuthIdFromBundle(claim);
        verifyNoInteractions(insuranceClaimsRepository, lineItemRepository);
    }

}
