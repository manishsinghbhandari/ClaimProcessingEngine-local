package io.coredge.health.claims.service;


import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.db.entity.BenefitsMaster;
import io.coredge.health.claims.db.entity.Member;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.app.DataNotFoundException;
import io.coredge.health.claims.helpers.HieHelper;
import io.coredge.health.claims.utils.FhirHelper;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class EligibilityService {

    private  final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private IGenericClient fhirClient;

    @Autowired
    private FhirContext fhirContext;

    @Autowired
    private HealthExchangeService hieService;

    @Autowired
    private IParser fhirParser;

    @Autowired
    private MemberService memberService;

    @Autowired
    private FhirHelper fhirHelper;

    @Autowired
    private BenefitMasterService benefitMasterService;

    @Autowired
    private HieHelper hieHelper;


    // TODO
    //  1. Check/Validate Eligibility ✅
    //  2. Patient Save/Update to DB ✅
    //  3. Save To FHIR  ✅
    //  4. Prepare FHIR Response  ✅
    //  -- Pending
    //  1. Add the intervention in the response  🏗️
    //      1.1. Adding the item in the response
    //      2.1. Setting value from the DB
    //  5. Return Response   ✅
    public String checkEligibilityS(String eligibilityJson) {
        try {
            // Parse input JSON to CoverageEligibilityRequest
            CoverageEligibilityRequest request = fhirParser.parseResource(CoverageEligibilityRequest.class, eligibilityJson);

            String patientId = request.getPatient().getReference().split("/")[1];
            // Validate eligibility
            log.info("Checking checkEligibility Patient Ref ID  : {}", patientId);
            String eligibilityResult = hieService.checkEligibility(patientId);
            boolean isEligible = isEligible(eligibilityResult);
             log.info(" Patient Ref ID  : {}, Eligibility result: {}", patientId, isEligible);
             try {
            // Fetch patient data
            String patientJson = hieHelper.getPatientFromHieViaShaNumber(patientId);
            // Parse patient JSON, save to DB, and store in FHIR if needed
            Patient patient = fhirContext.newJsonParser().parseResource(Patient.class, patientJson);
            if (patient != null) {
                Member member = memberService.saveToDatabase(Member.fromJsonBundle(patientJson));
                if (Boolean.FALSE.equals(fhirHelper.isPatientExistsInFhir(patientId))) {
                    fhirClient.create().resource(patient).execute();
                }
            }else {
                log.error("Patient Records Not found for Patient Reference ID : {}", patientId);
                throw  new DataNotFoundException("Patient Records Not found for Patient Reference ID "+ request.getPatient().getReference());
            }}catch (Exception e){
                 log.error("Exception while accessing HIE service : {}", patientId);
                 throw new RuntimeException("Exception while accessing HIE service :" + e.getMessage());
             }
            // Prepare the response
            CoverageEligibilityResponse response = createEligibilityResponse(request, isEligible, eligibilityResult);
            MethodOutcome responseOutcome = fhirClient.create().resource(response).execute();
            log.info("Saved CoverageEligibilityRequest ID: {}", responseOutcome.getId().getIdPart());
            return fhirParser.encodeResourceToString(response);
        } catch (Exception ex) {
            log.error("Error processing eligibility check: ", ex);
            throw new BadRequestException("$$"+ex.getMessage());
        }
    }




    private CoverageEligibilityResponse createEligibilityResponse(CoverageEligibilityRequest request, boolean isEligible , String eligibilityResponse ) {
        JsonNode messageNode = null;
        try {
            messageNode = objectMapper.readTree(eligibilityResponse).path("message");
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        // TODO Getting  policy details is fetch from the eligible API. Need to confirm is this correct way.
        // isEligible = true; For only test
        CoverageEligibilityResponse response = new CoverageEligibilityResponse()
                .setStatus(CoverageEligibilityResponse.EligibilityResponseStatus.ACTIVE)
                .setPatient(request.getPatient())
                .setCreated(new Date())
                .setRequest(new Reference(request.getIdElement().getValue()))
                .setInsurer(new Reference(request.getProvider().getReference())) // actual insurer reference
                .setOutcome(isEligible ? Enumerations.RemittanceOutcome.COMPLETE : Enumerations.RemittanceOutcome.ERROR)
                .setDisposition(isEligible ? "Eligibility verified and valid." : "Insurance not valid or not found.");
        if (isEligible) {
            CoverageEligibilityResponse.InsuranceComponent insuranceComponent = new CoverageEligibilityResponse.InsuranceComponent()
                    .setCoverage(new Reference(request.getInsurance().getFirst().getCoverage().getReference()))
                    .setInforce(true)
                    .setBenefitPeriod(new Period()
                            .setStart(FhirHelper.convertStringToDate(messageNode.get("policy_start").asText()))
                            .setEnd(FhirHelper.convertStringToDate(messageNode.get("policy_end").asText()))); // 1-year period

            request.getItem().forEach(requestItem -> {
                Coding coding = requestItem.getProductOrService().getCoding().getFirst();
                BenefitsMaster interventionDetails = benefitMasterService.fetchByInterventionCode(coding.getCode());
                String tariffs = interventionDetails.getTariffs();
                if(tariffs.contains("Upto")){
                    tariffs = tariffs.replace("Upto", "");
                }
                CoverageEligibilityResponse.ItemsComponent responseItem = new CoverageEligibilityResponse.ItemsComponent()
                        .setCategory(requestItem.getCategory())
                        .setProductOrService(requestItem.getProductOrService())
                        .addBenefit(new CoverageEligibilityResponse.BenefitComponent()
                                .setType(new CodeableConcept().addCoding(
                                        new Coding(coding.getSystem(), interventionDetails.getInterventionCode(),
                                                interventionDetails.getInterventionName())))
                                .setAllowed(new Money().setValue(Long.parseLong(tariffs.isEmpty() ? "0" : tariffs)).setCurrency(Constants.CURRENCY_SHILLING)) // TODO, still verification needed
                                .setUsed(new Money().setValue(0).setCurrency(Constants.CURRENCY_SHILLING)) // TODO. Where to get used money data

                        );
                insuranceComponent.addItem(responseItem);
            });

            response.addInsurance(insuranceComponent);
        } else {
            response.addError().setCode(new CodeableConcept().setText("Insurance not valid or not found"));
        }

        return response;
    }


    // Method to parse the input JSON and extract the 'message' node
    private boolean isEligible(String jsonInput) {
        JsonNode messageNode = null;
        try {
            messageNode = objectMapper.readTree(jsonInput).path("message");

            // TODO, Getting the unstable data from the HIE. If person is eligible still getting the reason not covered
//            String reason = messageNode.path("reason").asText();
//            if(!reason.isEmpty()){
//                throw new BadRequestException(reason);
//            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        int eligible = messageNode.path("eligible").asInt(0);
        int eligibleEmployee = messageNode.path("eligible_employee").asInt(0);
        int eligibleNhif = messageNode.path("eligible_nhif").asInt(0);
        // Return true if any of the attributes is 1, indicating eligibility
        return eligible == 1 || eligibleEmployee == 1 || eligibleNhif == 1;
    }


}
