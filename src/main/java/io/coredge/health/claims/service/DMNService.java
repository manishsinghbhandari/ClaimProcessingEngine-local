package io.coredge.health.claims.service;

import io.coredge.health.claims.db.entity.Intervention;
import io.coredge.health.claims.db.repositories.InterventionsRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.misc.OrderedHashSet;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.model.dmn.HitPolicy;
import org.camunda.bpm.model.dmn.instance.*;
import org.hl7.fhir.r4.model.Claim;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DMNService {

    public static final List<String> INTERVENTION_FILTER_KEYS = List.of("facilityCategory");

    @Autowired private InterventionsRepository interventionsRepo;
    private static final String SEPARATOR = ",";

    @Value("${CLAIM_AUTOMATION_DECISION_FILE:ShaClaimApprovalDecisionV2.dmn}")
    private String CLAIM_AUTOMATION_DECISION_FILE;

    @Value("${PREAUTH_AUTOMATION_DECISION_FILE:ShaPreAuthApprovalDecisionV2.dmn}")
    private String PREAUTH_AUTOMATION_DECISION_FILE;

    @Autowired
    ResourceLoader resourceLoader;

    private Map<String, List<Map<String, String>>> claimDecisionRulesMapByRuleId = new HashMap<>();

    private Map<String, List<Map<String, String>>> claimDecisionRulesMapByCode = new HashMap<>();

    private Map<String, List<Map<String, String>>> preAuthDecisionRulesMapByRuleId = new HashMap<>();

    private Map<String, List<Map<String, String>>> preAuthDecisionRulesMapByCode = new HashMap<>();

    /**
     * Get Decision Rule json format by Id and Type.
     * @param ruleId
     * @return Map<String, String>
     */
    public Map<String, String> getRuleJsonById(String ruleId, Claim.Use use, Map<String, Object> filters) {
        List<Map<String, String>> ruleJsons = new ArrayList<>();
        if (use == Claim.Use.CLAIM) {
            ruleJsons = claimDecisionRulesMapByRuleId.get(ruleId);
        } else if (use.equals(Claim.Use.PREAUTHORIZATION)) {
            ruleJsons = preAuthDecisionRulesMapByRuleId.get(ruleId);
        }

        if (ruleJsons == null || ruleJsons.isEmpty()) {
            return Collections.emptyMap();
        } else if (ruleJsons.size() == 1) {
            return ruleJsons.getFirst();
        }

        List<Map<String, String>> filteredRuleJsons = ruleJsons.stream().filter(ruleJson -> filterRule(ruleJson, filters)).toList();
        if(filteredRuleJsons.isEmpty()){
            return ruleJsons.getFirst();
        }
        return filteredRuleJsons.getFirst();
    }

    private boolean filterRule(Map<String, String> rule, Map<String, Object> filters){
        return filters.entrySet().stream().allMatch(entry -> evaluateRule(rule.get(entry.getKey()),entry.getValue()));
    }

    //TODO : currently it handle for "facilityCategory"
    private boolean evaluateRule(String rule , Object input){
        String[] validInputs = rule.split(",");
        return (Arrays.stream(validInputs).anyMatch(value -> StringUtils.containsIgnoreCase(value , (String) input)));
    }


    /**
     * Get Decision Rule json format by Sha Code and Type.
     * @param shaCode
     * @param use
     * @return Map<String, String>
     */
    public Map<String, String> getRuleJsonByCode(String shaCode, Claim.Use use , Map<String, Object> filters){
        List<Map<String, String>> ruleJsons = new ArrayList<>();
        if(use == Claim.Use.CLAIM){
            ruleJsons = claimDecisionRulesMapByCode.get(shaCode);
        }else if(use.equals(Claim.Use.PREAUTHORIZATION)){
            ruleJsons = preAuthDecisionRulesMapByCode.get(shaCode);
        }

        if (ruleJsons == null || ruleJsons.isEmpty()) {
            return Collections.emptyMap();
        } else if (ruleJsons.size() == 1) {
            return ruleJsons.getFirst();
        }

        List<Map<String, String>> filteredRuleJsons = ruleJsons.stream().filter(ruleJson -> filterRule(ruleJson, filters)).toList();
        if(filteredRuleJsons.isEmpty()){
            return ruleJsons.getFirst();
        }
        return filteredRuleJsons.getFirst();
    }

    public Map<String, List<Map<String, String>>> getClaimDecisionRules(){
        return this.claimDecisionRulesMapByCode;
    }

    public Map<String,List<Map<String, String>>> getPreAuthDecisionRules(){
        return this.preAuthDecisionRulesMapByCode;
    }

    /**
     * Executed when this bean is created to load DMNs into memory.
     */
    @PostConstruct
    public void postConstruct(){
        try {
            File file = readFileFromResources(PREAUTH_AUTOMATION_DECISION_FILE);
            readClaimDecisionFromDmn(file, preAuthDecisionRulesMapByCode, preAuthDecisionRulesMapByRuleId);

            file = readFileFromResources(CLAIM_AUTOMATION_DECISION_FILE);
            readClaimDecisionFromDmn(file, claimDecisionRulesMapByCode, claimDecisionRulesMapByRuleId);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public File readFileFromResources(String filename) throws IOException {
        log.info("Loading DMN File : {}", filename);
        Resource resource = resourceLoader.getResource("classpath:" + filename);
        File tempFile = Files.createTempFile("resource_", "_" + filename).toFile();
        tempFile.deleteOnExit();
        try (var in = resource.getInputStream(); var out = new FileOutputStream(tempFile)) {
            in.transferTo(out);
        }
        log.info("Loaded DMN File : {}", filename);
        return tempFile;
    }

    /**
     * load Decision rules into memory by reading Decision File from classpath.
     * @return
     */
    public void readClaimDecisionFromDmn(File dmnFile, Map<String, List<Map<String, String>>> storeByCode,
                                         Map<String, List<Map<String, String>>> storeByRuleId){
        DmnModelInstance modelInstance = Dmn.readModelFromFile(dmnFile);
        // Extract decisions
        Collection<Decision> decisions = modelInstance.getModelElementsByType(Decision.class);
        Set<String> ruleHeadingsSet = new OrderedHashSet<>(); // Important to keep it ordered.
        for (Decision decision : decisions) {
           log.info("Decision ID: {} and Name: {}", decision.getId(), decision.getName());

            // Extract decision table
            Collection<DecisionTable> decisionTables = decision.getChildElementsByType(DecisionTable.class);
            DecisionTable decisionTable = decisionTables.iterator().next();
            if (decisionTable != null) {
                log.info("Decision Table ID: {}", decisionTable.getId());

                // Extract input and output columns
                Collection<Input> inputs = decisionTable.getInputs();
                Collection<Output> outputs = decisionTable.getOutputs();
                Map<String, String> ruleJsonMap = null;

                // Iterate 1st Input list to find Rule headings.
                for (Input input : inputs) {
                    //log.info("Input ID: {} Label {}", input.getId(), input.getLabel());
                    //ruleHeadingsSet.add(input.getLabel());
                    ruleHeadingsSet.add(input.getInputExpression().getText().getTextContent());
                }

                for (Output output : outputs) {
                    //log.info("  Output ID: {}  and Name: {}", output.getId(), output.getName());
                    ruleHeadingsSet.add(output.getLabel()); // Add output heading as well.
                }

                // Extract rules
                Collection<Rule> rules = decisionTable.getRules();
                //log.info("Rules:");
                for (Rule rule : rules) {
                    log.debug("  Rule ID: {} ", rule.getId());
                    // Note: Important to reset this map to empty.
                    ruleJsonMap = new LinkedHashMap<>();
                    Collection<InputEntry> inputEntries = rule.getInputEntries();
                    Collection<OutputEntry> outputEntries = rule.getOutputEntries();

                    //Note: Important to reset this Iterator everytime for each rule.
                    Iterator<String> headingIterator = ruleHeadingsSet.iterator();
                    //log.info("    Input Conditions:");
                    String ruleHeading = headingIterator.next().replace("\"","");
                    for (InputEntry inputEntry : inputEntries) {
                        String ruleContent = inputEntry.getTextContent().replace("\"", "");
                        ruleJsonMap.put(ruleHeading, ruleContent);
                        //log.info("      Condition: " + inputEntry.getTextContent());
                        ruleHeading = headingIterator.next();
                    }

                    //log.info("    Output Values:");
                    for (OutputEntry outputEntry : outputEntries) {
                        //log.info("      Value: " + outputEntry.getTextContent());
                        ruleJsonMap.put(ruleHeading, outputEntry.getTextContent());
                        //Note we are supporting multiple outputs.
                        if(headingIterator.hasNext())
                            headingIterator.next();
                    }

                    // Update it into hashMap to keep it in memory.
                    String InterventionCode = inputEntries.iterator().next().getTextContent().replace("\"", "");
                    //storeByCode.put(InterventionCode, ruleJsonMap);
                    storeByCode.computeIfAbsent(InterventionCode, k -> new ArrayList<>()).add(ruleJsonMap);
                    //log.info("Loaded Rule for Code {} with Key {}", InterventionCode, ruleJsonMap);
                    //storeByRuleId.put(rule.getId(), ruleJsonMap);
                    storeByRuleId.computeIfAbsent(rule.getId(), k -> new ArrayList<>()).add(ruleJsonMap);
                }
            }
        }
    }
    public String generateInterventionDMN(String outputPath, boolean isPreAuthColumnNeeded) {
        // Create a new DMN model instance
        DmnModelInstance dmnModelInstance = Dmn.createEmptyModel();

        // Define the DMN model structure
        Definitions definitions = dmnModelInstance.newInstance(Definitions.class);
        definitions.setNamespace("http://camunda.org/schema/1.0/dmn");
        definitions.setId("definitions");
        definitions.setName("InterventionDecision");
        dmnModelInstance.setDefinitions(definitions);

        // Define the decision table
        Decision decision = dmnModelInstance.newInstance(Decision.class);
        decision.setId("Gen-Sha-Pre-Auth-Decision");
        decision.setName("Genereated SHA Decision Table");
        definitions.addChildElement(decision);

        DecisionTable decisionTable = dmnModelInstance.newInstance(DecisionTable.class);
        decisionTable.setHitPolicy(HitPolicy.FIRST);
        decision.addChildElement(decisionTable);

        // Define Input Columns
        createInputColumn(dmnModelInstance, decisionTable, "code", "string", null);
        createInputColumn(dmnModelInstance, decisionTable, "serviceCategory", "string", getSeriveCategoryValues());
        createInputColumn(dmnModelInstance, decisionTable, "facilityCategory", "string", getFacilityCategoryValues());
        createInputColumn(dmnModelInstance, decisionTable, "gender", "string", getGenderValues());
        createInputColumn(dmnModelInstance, decisionTable, "age", "integer", null);
        createInputColumn(dmnModelInstance, decisionTable, "interventionUsageCount", "integer", null);
        createInputColumn(dmnModelInstance, decisionTable, "claimAmount", "integer", null);
        createInputColumn(dmnModelInstance, decisionTable, "individualUsage", "integer", null);
        createInputColumn(dmnModelInstance, decisionTable, "householdUsage", "integer", null);

        if(isPreAuthColumnNeeded)
            createInputColumn(dmnModelInstance, decisionTable, "needsPreAuth", "boolean", null);

        // Define Output Columns
        createOutputColumn(dmnModelInstance, decisionTable, "isAutoApproved", "boolean");

        for(Intervention benefit: interventionsRepo.findAll())
            createRule(dmnModelInstance, decisionTable, benefit, isPreAuthColumnNeeded);

        // Write the DMN model to a file
        File file = new File(outputPath);
        Dmn.writeModelToFile(file, dmnModelInstance);
        System.out.println("DMN table generated at: " + file.getAbsolutePath());
        return dmnModelInstance.toString();
    }

    /**
     * Create Input Column in DMN table based on given data type and name.
     * @param modelInstance
     * @param decisionTable
     * @param name
     * @param typeRef
     */
    private void createInputColumn(DmnModelInstance modelInstance, DecisionTable decisionTable,
                                   String name, String typeRef, Set<String> values) {
        Input input = modelInstance.newInstance(Input.class);
        input.setLabel(name);
        InputExpression inputExpression = modelInstance.newInstance(InputExpression.class);

        Text text = modelInstance.newInstance(Text.class);
        text.setTextContent(name);
        inputExpression.setText(text);

        inputExpression.setTypeRef(typeRef);
        input.setInputExpression(inputExpression);
        if(null != values && !values.isEmpty()){
            InputValues inputValues = modelInstance.newInstance(InputValues.class);
            Text inputValuesText = modelInstance.newInstance(Text.class);
            inputValuesText.setTextContent(values.stream()
                    .map(value -> "\"" + value + "\"")
                    .reduce((value1, value2) -> value1 + "," + value2)
                    .orElse(""));
            inputValues.setText(inputValuesText);
            input.setInputValues(inputValues);
        }
        decisionTable.addChildElement(input);
    }

    /**
     * Create Output column in DMN based on name and type provided.
     * @param modelInstance
     * @param decisionTable
     * @param name
     * @param typeRef
     */
    private void createOutputColumn(DmnModelInstance modelInstance, DecisionTable decisionTable, String name, String typeRef) {
        Output output = modelInstance.newInstance(Output.class);
        output.setName(name);
        output.setTypeRef(typeRef);
        decisionTable.addChildElement(output);
    }

    /**
     * Create a Rule in provided DMN instance.
     *
     * @param modelInstance
     * @param decisionTable
     * @param intervention
     * @param isPreAuthColumnNeeded
     */
    private void createRule(DmnModelInstance modelInstance, DecisionTable decisionTable,
                            Intervention intervention, boolean isPreAuthColumnNeeded) {
        Rule rule = modelInstance.newInstance(Rule.class);

        rule.addChildElement(createInputEntry(modelInstance, intervention.getInterventionCode()));
        // Note handle empty accessCodes.

        rule.addChildElement(createInputEntry(modelInstance, intervention.getAllowedAccessCodes()));
        rule.addChildElement(createInputEntry(modelInstance, intervention.getAllowedFacilityCategories()));
        // Note: handle age based on min and max values.
        String ageRule = "";
        Double tarrif = intervention.getTariff();
        if(null != intervention.getMinAge() && null != intervention.getMaxAge()
                && intervention.getMaxAge() > 0 && intervention.getMinAge() > 0){
            ageRule = "[" + intervention.getMinAge() + ".." + intervention.getMaxAge() + "]";
        }else if(null != intervention.getMaxAge() && intervention.getMaxAge() > 0){
            ageRule = "<=" + intervention.getMaxAge();
        }else if(null != intervention.getMinAge() && intervention.getMinAge() > 0){
            ageRule = ">=" + intervention.getMinAge();
        }
        rule.addChildElement(createInputEntry(modelInstance, intervention.getAllowedGenders()));
        rule.addChildElement(createInputEntry(modelInstance, ageRule));

        rule.addChildElement(createInputEntry(modelInstance, intervention.getIndividualLimitCount()));
        if(tarrif == null || tarrif == 0){
            rule.addChildElement(createInputEntry(modelInstance, null));
        }else {
            rule.addChildElement(createInputEntry(modelInstance, intervention.getTariff()));
        }
        rule.addChildElement(createInputEntry(modelInstance, intervention.getIndividualLimitAmount()));
        rule.addChildElement(createInputEntry(modelInstance, intervention.getHouseholdLimitAmount()));

        if(isPreAuthColumnNeeded)
            rule.addChildElement(createInputEntry(modelInstance, intervention.isNeedsPreAuth()));

        // Finally create output entry
        rule.addChildElement(createOutputEntry(modelInstance, true));

        decisionTable.addChildElement(rule);
    }

    /**
     * Create an Input entry for a DMN column.
     * @param modelInstance
     * @param value
     * @return InputEntry
     */
    private InputEntry createInputEntry(DmnModelInstance modelInstance, Object value) {
        InputEntry inputEntry = modelInstance.newInstance(InputEntry.class);
        Text text = modelInstance.newInstance(Text.class);
        String content = value == null ? "": value.toString();
        //Note: put a double quotes string.
        if(value instanceof String) {
            if(((String) value).startsWith("[") || ((String) value).startsWith("&")
                    || ((String) value).startsWith(">") || ((String) value).startsWith("<")
                    || ((String) value) == null){
                //Do not change these.
            }else{
                content = "\"" + content + "\"";
            }
        } else if(value instanceof List){
            java.util.List<String> values = (java.util.List<String>) value;
            content = values.stream()
                    .map(item -> "\"" + item.toLowerCase() + "\"") // Wrap each item in quotes
                    .collect(Collectors.joining(SEPARATOR));;
        }else if (value instanceof Double){
            content = String.valueOf(value);
        }
        text.setTextContent(content);
        inputEntry.setText(text);
        return inputEntry;
    }

    /**
     * Create an Output Entry in given DMN instance.
     * @param modelInstance
     * @param value
     * @return OutputEntry
     */
    private OutputEntry createOutputEntry(DmnModelInstance modelInstance, Object value) {
        OutputEntry outputEntry = modelInstance.newInstance(OutputEntry.class);
        Text text = modelInstance.newInstance(Text.class);
        text.setTextContent(value.toString());
        outputEntry.setText(text);
        return outputEntry;
    }

    /**
     * Get possible values for ServiceCategory
     * @return Set of ServiceCategories
     */
    private Set<String> getSeriveCategoryValues(){
        return Set.of("inpatient", "outpatient");
    }

    /**
     * Get possible values for FacilityCategory
     * @return Set of FacilityCategories
     */
    private Set<String> getFacilityCategoryValues(){
        return Set.of("level 1", "level 2","level 3","level 4","level 5","level 6");
    }

    /**
     * Get possible values for Gender
     * @return Set of Genders
     */
    private Set<String> getGenderValues(){
        return Set.of("male", "female");
    }

    public static Map<String , Object> filtersData(Map<String, Object> inputs){
        return INTERVENTION_FILTER_KEYS.stream().filter(inputs::containsKey).collect(Collectors.toMap(Function.identity() , inputs::get));
    }
}
