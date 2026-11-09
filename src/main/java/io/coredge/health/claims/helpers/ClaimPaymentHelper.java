package io.coredge.health.claims.helpers;


import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import io.coredge.health.claims.config.properties.ApplicationProperties;
import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.db.entity.ErpClaims;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.models.eclaims.CreateClaimFundScheme;
import io.coredge.health.claims.service.ErpClaimsService;
import io.coredge.health.claims.service.PostOfficeService;
import io.coredge.health.claims.service.ProcessNotesService;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.codesystems.Adjudication;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static io.coredge.health.claims.constants.Constants.PaymentType.COMPLETE;
import static io.coredge.health.claims.constants.Constants.PaymentType.PARTIAL;
import static io.coredge.health.claims.enums.ExtensionStateCodeEnum.PAYMENT_PARTIAL;


@Slf4j
@Component
public class ClaimPaymentHelper {

    private final PostOfficeService postOfficeService;
    private final IGenericClient fhirClient;
    private final ProcessNotesService processNotesService;
    private final ErpClaimsService erpClaimsService;
    private final ApplicationProperties.ExternalServices externalServicesProperties;

    public ClaimPaymentHelper(PostOfficeService postOfficeService, IGenericClient fhirClient,
                              ProcessNotesService processNotesService, ErpClaimsService erpClaimsService,
                              ApplicationProperties.ExternalServices externalServicesProperties) {
        this.postOfficeService = postOfficeService;
        this.fhirClient = fhirClient;
        this.processNotesService = processNotesService;
        this.erpClaimsService = erpClaimsService;
        this.externalServicesProperties = externalServicesProperties;
    }

    /**
     * Retrieves a Claim object for further processing.
     *
     * <p>This method currently returns a Claim object from FHIR client service.
     * A future implementation will involve logging in and fetching the Claim
     * object from an external source, such as a FHIR client service.</p>
     *
     * @return a Claim object retrieved from the tester service.
     * @throws IllegalStateException if the Claim object cannot be retrieved
     *                               from the tester service.
     */
    public Claim getClaimObject(String claimId) {
        log.info("Fetching claim for claimID :{}", claimId);
        Claim claim = null;
        try {
            claim = fhirClient.read().resource(Claim.class).withId(claimId).execute();
            if (claim.hasCareTeam()) {
                log.info("Claim with claimId: {} is loaded", claimId);
            }
        } catch (ResourceNotFoundException e) {
            throw new BadRequestException(e.getMessage());
        }
        return claim;
    }

    /**
     * Retrieves a {@link ClaimResponse} object based on the claim ID provided within an {@link ActivatedJob}.
     *
     * <p>This method extracts the {@code claimId} variable from the given {@link ActivatedJob},
     * then uses it to fetch the corresponding {@link ClaimResponse} via the {@code postOfficeService}.
     * The loaded claim response is logged for confirmation.</p>
     *
     * @param claimId the {@link String} containing the claim ID as a variable.
     * @return the {@link ClaimResponse} object associated with the specified claim ID.
     * @throws NullPointerException if the claim ID is missing or no matching {@link ClaimResponse} is found.
     */
    public ClaimResponse getClaimResponseObject(String claimId) {
        ClaimResponse claimResponse = postOfficeService.getClaimResponseByClaimId(claimId, null);
        log.info("ClaimResponse with claimId: {} is loaded", claimId);
        return claimResponse;
    }

    // Method to find claimResponse by ClaimResponseId
    public ClaimResponse getClaimResponseById(String claimResponseId) {
        ClaimResponse claimResponse = null;
        try {
            claimResponse = fhirClient.read().resource(ClaimResponse.class).withId(claimResponseId).execute();
        } catch (ResourceNotFoundException e) {
            log.info("ClaimResponse Resource with ID: {} not found", claimResponseId);
        }
        return claimResponse;
    }

    /**
     * Processes a {@link ClaimResponse} to extract adjudicated amounts for each item sequence.
     *
     * <p>This method iterates through the items in the {@link ClaimResponse} and groups them by
     * {@code itemSequence}. For each group, if multiple items exist, it filters by the adjudication
     * category "Benefit" and selects the first matching item. For the selected item, it retrieves
     * the adjudicated amount if available and adds it to the result map.</p>
     *
     * @param claimResponse the {@link ClaimResponse} object containing the list of items to be processed.
     * @return a map with {@link Integer} keys representing each item sequence and {@link BigDecimal}
     * values representing the adjudicated amount for that sequence.
     * If no amount is found for an item, it is excluded from the map.
     */
    public Map<Integer, BigDecimal> processClaimResponseGetAmount(ClaimResponse claimResponse) {
        List<ClaimResponse.ItemComponent> items = claimResponse.getItem();
        Map<Integer, BigDecimal> resultMap = new HashMap<>();
        items.stream().collect(Collectors.groupingBy(ClaimResponse.ItemComponent::getItemSequence)).forEach((itemSequence, itemList) -> {
            // Filter by "BENEFIT" if multiple items exist
            ClaimResponse.ItemComponent item = (itemList.size() > 1) ? itemList.stream().filter(i -> i.getAdjudication().stream().anyMatch(adj ->
                    Adjudication.BENEFIT.toCode().equals(adj.getCategory().getText()))).findFirst().orElse(null) : itemList.getFirst();
            if (item != null) {
                item.getAdjudication().stream().filter(ClaimResponse.AdjudicationComponent::hasAmount).findFirst().ifPresent(adj -> resultMap.put(itemSequence, adj.getAmount().getValue()));
            }
        });
        return resultMap;
    }

    public ClaimResponseStatus addPaymentComponentToClaimResponse(Claim claim, ClaimResponse claimResponse, CreateClaimFundScheme requestCallBack) {
        // Update the ClaimResponseObject With Payment Components
        // Fetch claimResponse by ID
        // Define payment details
        log.info("inside addPaymentComponentToClaimResponse for claimId : {}", claim.getIdPart());
        String paymentType = COMPLETE;// Payment type (use code if needed),
        //TODO  partial logic need to add
        Money adjustment = new Money();
        BigDecimal adjustmentAmount = requestCallBack.getAdjustmentAmount();
        String adjustmentReason = ""; // Reason for adjustment
        if (adjustmentAmount != null && adjustmentAmount.compareTo(BigDecimal.ZERO) > 0) {
            adjustment.setValue(requestCallBack.getAdjustmentAmount()); // Adjustment amount
            adjustment.setCurrency(Constants.CURRENCY_SHILLING);
            adjustmentReason = requestCallBack.getAdjustmentReason();
            paymentType = PARTIAL;
        }

        // Added for bifurcation
        ErpClaims parentClaim = erpClaimsService.findByClaimNo(requestCallBack.getClaimNo());
        PaymentStatus paymentStatus = null;
        if (parentClaim != null && (parentClaim.getHasChild() != null && parentClaim.getHasChild())) {
            // Bifurcation Logic.
             paymentStatus = determineAggregatePaymentStatus(parentClaim, requestCallBack.getClaimNo(), requestCallBack.getClaimAmount());
             paymentType = paymentStatus.aggregateStatus;
        } else {
           paymentStatus = new PaymentStatus(paymentType, false, requestCallBack.getClaimAmount(),  requestCallBack.getClaimAmount());
        }

        Date paymentDate = new Date(); // Payment date
        Money amount = new Money();
        amount.setValue(requestCallBack.getClaimAmount()); // Payable amount
        amount.setCurrency(Constants.CURRENCY_SHILLING);

        String identifierSystem = requestCallBack.getIdentifierSystem();
        String identifierValue = requestCallBack.getIdentifierValue();


        // get Fund type
        String fundType = getFundSchemeTypeWithAmount(requestCallBack.getFundScheme());

        // Call the addPaymentDetails method
        claimResponse = addPaymentDetails(claim,
                claimResponse,
                paymentType,
                adjustment,
                adjustmentReason,
                paymentDate,
                amount,
                identifierSystem,
                identifierValue,
                requestCallBack.getRejection_reason(),
                fundType
        );
        log.info("return ");
        return new ClaimResponseStatus(claimResponse, paymentStatus);
    }

    public String getFundSchemeTypeWithAmount(List<CreateClaimFundScheme.FundScheme> schemes) {
        if (schemes == null || schemes.isEmpty()) {
            return null;
        }
        return schemes.stream()
                .filter(s -> s.getAmount() != null && s.getAmount().compareTo(BigDecimal.ZERO) > 0)
                .map(CreateClaimFundScheme.FundScheme::getFundSchemeType)
                .findFirst()
                .orElse(null);
    }

    public record ClaimResponseStatus(ClaimResponse claimResponse, PaymentStatus paymentStatus) {
    }

    private PaymentStatus determineAggregatePaymentStatus(ErpClaims parentClaim, String callBackClaimID, BigDecimal erpPaidAmount) {

        List<ErpClaims> childClaims = erpClaimsService.findByParentClaimNo(parentClaim.getClaimNo());
        // Combine parent + children
        List<ErpClaims> allClaims = new ArrayList<>();
        allClaims.add(parentClaim);
        allClaims.addAll(childClaims);
        boolean allDeclined = allClaims.stream()
                .filter(claim -> !claim.getClaimNo().equals(callBackClaimID))
                .allMatch(claim ->
                        ExtensionStateCodeEnum.PAYMENT_DECLINED.name().equalsIgnoreCase(claim.getPaymentStatus())
                );

        // Get only Paid amount
        BigDecimal paidAmount = allClaims.stream()
                .filter(claim -> !claim.getClaimNo().equals(callBackClaimID) && (ExtensionStateCodeEnum.PAYMENT_COMPLETED.name().equalsIgnoreCase(claim.getPaymentStatus())
                        || PAYMENT_PARTIAL.name().equalsIgnoreCase(claim.getPaymentStatus())))
                .map(ErpClaims::getClaimAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaidAmountIncludingPaidNow;
        if (paidAmount.compareTo(BigDecimal.ZERO) > 0) {
            totalPaidAmountIncludingPaidNow = paidAmount.add(erpPaidAmount);
        } else {
            totalPaidAmountIncludingPaidNow = erpPaidAmount;
        }
        // Get Complete amount
        BigDecimal totalAmount = allClaims.stream().map(ErpClaims::getClaimAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

        String aggregateStatus = totalAmount.compareTo(totalPaidAmountIncludingPaidNow) == 0 ? COMPLETE : PARTIAL;
        return new PaymentStatus(aggregateStatus, allDeclined, paidAmount, totalAmount);
    }

    public record PaymentStatus(String aggregateStatus, Boolean isAllDeclined, BigDecimal paidAmount,
                                BigDecimal totalAmount) {
    }

    public ClaimResponse addPaymentDetails(Claim claim, ClaimResponse claimResponse, String paymentType,
                                           Money adjustment, String adjustmentReason,
                                           Date paymentDate, Money amount, String identifierSystem,
                                           String identifierValue, String rejectionReason, String fundType) {
        ClaimResponse.PaymentComponent paymentComponent = new ClaimResponse.PaymentComponent();

        // Setting the payment type
        CodeableConcept type = new CodeableConcept();
        type.setText(paymentType);  // You may want to use specific codes instead of text
        paymentComponent.setType(type);

        // Setting adjustment if any
        paymentComponent.setAdjustment(adjustment);

        // Setting adjustment reason
        CodeableConcept reason = new CodeableConcept();
        reason.setText(adjustmentReason);
        paymentComponent.setAdjustmentReason(reason);

        // Setting the payment date
        paymentComponent.setDate(paymentDate);

        // Setting the payable amount after adjustment
        paymentComponent.setAmount(amount);

        // Setting the business identifier for payment
        Identifier identifier = new Identifier();
        identifier.setSystem(identifierSystem);
        identifier.setValue(identifierValue);
        paymentComponent.setIdentifier(identifier);

        // Adding the payment component to the claim response
        claimResponse.setPayment(paymentComponent);

        // Setting the rejection reason
        // Adding the rejection reason if provided
        if (rejectionReason != null && !rejectionReason.isEmpty()) {
            String rejectionNote = String.format(
                    "Payment Rejected, FundType: %s, Amount: %.2f %s, Reason: %s",
                    fundType != null ? fundType : "N/A",
                    amount.getValue(), amount.getCurrency(),
                    rejectionReason);
            ClaimResponse.NoteComponent paymentRejectionsNote = new ClaimResponse.NoteComponent();
            paymentRejectionsNote.setNumber(claimResponse.getProcessNote().size() + 1);
            paymentRejectionsNote.setType(Enumerations.NoteType.DISPLAY);
            paymentRejectionsNote.setText(rejectionNote);
            claimResponse.addProcessNote(paymentRejectionsNote);
            processNotesService.saveProcessNotes(claim.getIdPart(), ExtensionStateCodeEnum.PAYMENT_DECLINED, rejectionNote);
        }

        return claimResponse;
    }

    public InvoiceDetails getInvoiceDetails(Claim claim) {
        String invoiceNumber = "";
        Date invoiceDate = null; // default to current date
        if (claim == null || claim.getExtension() == null) {
            log.warn("Claim or claim extensions are null");
            return new InvoiceDetails(invoiceNumber, invoiceDate);
        }
        Optional<Extension> invoiceExtension = claim.getExtension().stream()
                .filter(extension -> externalServicesProperties.getTerminologyServerBaseUrl()
                        .concat("/StructureDefinition/extension-patientInvoice")
                        .equalsIgnoreCase(extension.getUrl()))
                .findFirst();

        if (invoiceExtension.isPresent()) {
            for (Extension nestedExtension : invoiceExtension.get().getExtension()) {
                switch (nestedExtension.getUrl()) {
                    case "invoiceNumber" -> {
                        if (nestedExtension.getValue() instanceof StringType stringType) {
                            invoiceNumber = stringType.getValue();
                            log.info("invoiceNumber {}", invoiceNumber);
                        }
                    }
                    case "invoiceDate" -> {
                        if (nestedExtension.getValue() instanceof DateType dateType) {
                            invoiceDate = dateType.getValue();
                            log.info("invoiceDate {}", invoiceDate);
                        }
                    }
                    default -> {
                        // ignore other extensions
                    }
                }
                // Break once both values are found
                if (!invoiceNumber.isEmpty() && invoiceDate != null) {
                    break;
                }

            }
        }
        return new InvoiceDetails(invoiceNumber, invoiceDate);
    }

    public String getInvoiceNumber(Claim claim) {
        String invoiceNumber = "";

        // Iterate over extensions to find the main URL
        for (Extension extension : claim.getExtension()) {
            if (extension.getUrl().contains("/StructureDefinition/extension-patientInvoice")) {
                // Iterate over nested extensions to find 'invoiceNumber'
                for (Extension nestedExtension : extension.getExtension()) {
                    if ("invoiceNumber".equals(nestedExtension.getUrl())) {
                        // Extract valueString for the invoiceNumber
                        if (nestedExtension.getValue() instanceof StringType) {
                            invoiceNumber = ((StringType) nestedExtension.getValue()).getValue();
                        }
                        break; // Exit loop after finding the invoice number
                    }
                }
                break; // Exit loop after processing the main extension
            }
        }
        log.info("claimId : {}, Invoice Number: {}", claim.getIdPart(),  invoiceNumber);
        return invoiceNumber;
    }



    // Method to extract the part after the colon if the string starts with "Provider Invoice"
    public String getPartAfterColon(String input) {
        if (input.contains("Provider Invoice")) {
            String[] parts = input.split(":");
            if (parts.length > 1) {
                return parts[1].trim(); // Return the part after the colon
            }
        }
        return null; // Return null if conditions are not met
    }

    public Date getInvoiceDate(Claim claim) {
        // Use pattern matching with Java 21 for cleaner iteration and type checks
        return claim.getExtension().stream()
                .filter(extension -> externalServicesProperties.getTerminologyServerBaseUrl().concat("/StructureDefinition/extension-patientInvoice").equalsIgnoreCase(extension.getUrl()))
                .flatMap(extension -> extension.getExtension().stream())
                .filter(nestedExtension -> "invoiceDate".equals(nestedExtension.getUrl()) && nestedExtension.getValue() instanceof DateType)
                .map(nestedExtension -> ((DateType) nestedExtension.getValue()).getValue())
                .findFirst()
                .orElseGet(Date::new); // Default to current date if no match is found
    }

    public record InvoiceDetails(String invoiceNumber, Date invoiceDate) {
    }
}
