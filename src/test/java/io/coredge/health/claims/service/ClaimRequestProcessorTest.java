package io.coredge.health.claims.service;

import io.coredge.health.claims.exception.base.BusinessException;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ClaimRequestProcessorTest {

    private static final String PRE_AUTH_ID = "c6bf4c09-8b0d-457d-a6d8-c644dddb39a5";
    private static final String ERROR_PRE_AUTH_ID_MISSING = "The Pre-Auth ID must not be blank in the bundle.";

    @InjectMocks
    private ClaimRequestProcessor claimRequestProcessor;


    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldReturnPreAuthIdWhenValidRelatedClaimExists() {
        // Given: A Claim with a valid Pre-Auth ID
        ReflectionTestUtils.setField(claimRequestProcessor, "validationEnabled", true);
        Claim newClaim = new Claim();
        newClaim.setRelated(List.of(getRelatedClaimComponent(PRE_AUTH_ID))); // Add valid "pre-auth" related claim

        // When: Fetching the Pre-Auth ID
        String preAuthId = claimRequestProcessor.fetchPreAuthIdFromBundle(newClaim);

        // Then: The correct Pre-Auth ID is returned
        assertEquals(PRE_AUTH_ID, preAuthId);
    }

    @Test
    void shouldThrowExceptionWhenPreAuthIdIsMissing() {
        // Given: A Claim with a "pre-auth" related claim but no valid ID (empty value)
        ReflectionTestUtils.setField(claimRequestProcessor, "validationEnabled", true);
        Claim newClaim = new Claim();
        newClaim.setRelated(List.of(getRelatedClaimComponent(""))); // Empty ID

        // When: Fetching the Pre-Auth ID
        Executable executable = () -> claimRequestProcessor.fetchPreAuthIdFromBundle(newClaim);

        // Then: A BusinessException is thrown
        BusinessException exception = assertThrows(BusinessException.class, executable);
        assertEquals(ERROR_PRE_AUTH_ID_MISSING, exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenNoMatchingPreAuthRelationship() {
        // Given: A Claim with related claims, but none with "pre-auth" relationship
        ReflectionTestUtils.setField(claimRequestProcessor, "validationEnabled", true);
        Claim newClaim = new Claim();
        newClaim.setRelated(List.of(getNonPreAuthRelatedClaimComponent())); // No "pre-auth" relationship

        // When: Fetching the Pre-Auth ID
        Executable executable = () -> claimRequestProcessor.fetchPreAuthIdFromBundle(newClaim);

        // Then: A BusinessException is thrown
        BusinessException exception = assertThrows(BusinessException.class, executable);
        assertEquals(ERROR_PRE_AUTH_ID_MISSING, exception.getMessage());
    }

    @Test
    void shouldReturnEmptyWhenValidationIsDisabled() {
        // Given: validationEnabled is false
        ReflectionTestUtils.setField(claimRequestProcessor, "validationEnabled", false);
        Claim newClaim = new Claim();
        newClaim.setRelated(List.of(getRelatedClaimComponent(PRE_AUTH_ID))); // Should be ignored

        // When: Fetching the Pre-Auth ID
        String preAuthId = claimRequestProcessor.fetchPreAuthIdFromBundle(newClaim);

        // Then: Should return an empty string (not null)
        assertEquals("", preAuthId);
    }

    @Test
    void shouldReturnEmptyWhenNoRelatedClaimsExist() {
        // Given: A Claim with no related claims
        ReflectionTestUtils.setField(claimRequestProcessor, "validationEnabled", true);
        Claim newClaim = new Claim();
        newClaim.setRelated(List.of()); // Empty related list

        // When: Fetching the Pre-Auth ID
        String preAuthId = claimRequestProcessor.fetchPreAuthIdFromBundle(newClaim);

        // Then: Should return an empty string (not throw an exception)
        assertEquals("", preAuthId);
    }



    @Test
    void testGetNetValueForFHIRClaimBundle() {
        // Create Claim
        Claim claim = new Claim();
        List<Claim.ItemComponent> items = new ArrayList<>();

        // First Item (Consultation)
        items.add(createItemComponent(new BigDecimal("1000"), new BigDecimal("1")));

        // Second Item (Laboratory Investigations)
        items.add(createItemComponent(new BigDecimal("2500"), new BigDecimal("1")));

        // Third Item (Prescription, Drug Administration, and Dispensing)
        items.add(createItemComponent(new BigDecimal("6600"), new BigDecimal("1")));

        // Set items to claim
        claim.setItem(items);

        // Calculate net value
        BigDecimal actualValue = claimRequestProcessor.getNetValue(claim);

        // Expected value: 1000 + 2500 + 6600 = 10100
        assertEquals(new BigDecimal("10100"), actualValue);
    }

    private Claim.ItemComponent createItemComponent(BigDecimal netValue, BigDecimal quantityValue) {
        Claim.ItemComponent claimComponent = new Claim.ItemComponent();

        // Set net value
        Money money = new Money();
        money.setValue(netValue);
        claimComponent.setNet(money);

        // Set quantity
        Quantity quantity = new Quantity();
        quantity.setValue(quantityValue);
        claimComponent.setQuantity(quantity);

        return claimComponent;
    }



    // Helper method to create a RelatedClaimComponent with a "pre-auth" relationship
    private static Claim.RelatedClaimComponent getRelatedClaimComponent(String preAuthId) {
        Claim.RelatedClaimComponent relatedClaimComponent = new Claim.RelatedClaimComponent();
        Reference relatedClaim = new Reference();
        Identifier identifier = new Identifier();
        identifier.setValue(preAuthId);
        relatedClaim.setIdentifier(identifier);
        relatedClaimComponent.setClaim(relatedClaim);

        // Add "pre-auth" relationship
        Coding relationshipCoding = new Coding();
        relationshipCoding.setCode("pre-auth");
        CodeableConcept relationshipConcept = new CodeableConcept();
        relationshipConcept.addCoding(relationshipCoding);
        relatedClaimComponent.setRelationship(relationshipConcept);
        return relatedClaimComponent;
    }

    // Helper method to create a RelatedClaimComponent without a "pre-auth" relationship
    private static Claim.RelatedClaimComponent getNonPreAuthRelatedClaimComponent() {
        Claim.RelatedClaimComponent relatedClaimComponent = new Claim.RelatedClaimComponent();
        Reference relatedClaim = new Reference();
        Identifier identifier = new Identifier();
        identifier.setValue(PRE_AUTH_ID);
        relatedClaim.setIdentifier(identifier);
        relatedClaimComponent.setClaim(relatedClaim);

        // Add a different relationship (not "pre-auth")
        Coding relationshipCoding = new Coding();
        relationshipCoding.setCode("other-type"); // Not "pre-auth"
        CodeableConcept relationshipConcept = new CodeableConcept();
        relationshipConcept.addCoding(relationshipCoding);
        relatedClaimComponent.setRelationship(relationshipConcept);
        return relatedClaimComponent;
    }

}
