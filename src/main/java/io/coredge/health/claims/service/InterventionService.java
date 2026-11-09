package io.coredge.health.claims.service;

import io.coredge.health.claims.db.entity.Intervention;
import io.coredge.health.claims.db.repositories.InterventionsRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.util.StringUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class InterventionService {
    @Autowired private InterventionsRepository interventionsRepo;

    List<String> allowedAccessCodes = List.of("inpatient", "outpatient");
    List<String> allowedFacilityCategories = List.of("level 1");
    List<String> allowedGenders = List.of("male", "female");

    public void importInterventions() throws IOException {
        ClassPathResource resource = new ClassPathResource("benefits_master.xlsx");

        try (InputStream is = resource.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            List<Intervention> interventions = new ArrayList<>();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {  // Skip header row
                Row row = sheet.getRow(i);
                if (row == null || row.getCell(0) == null) continue;

                allowedAccessCodes = new ArrayList<String>();
                allowedFacilityCategories = new ArrayList<String>();
                allowedGenders =new ArrayList<String>();

                Intervention intervention = new Intervention();
                intervention.setInterventionCode(row.getCell(0).getStringCellValue());
                intervention.setShaCategory(row.getCell(1).getStringCellValue());
                intervention.setCategoryCode(row.getCell(22).getStringCellValue());

                if(!StringUtil.isBlank(row.getCell(2).getStringCellValue())){
                    allowedAccessCodes.clear();
                    allowedAccessCodes = List.of(row.getCell(2).getStringCellValue().split(","));
                }

                if(!StringUtil.isBlank(row.getCell(5).toString())){
                    allowedFacilityCategories.clear();
                    List<String> allowedFacilities = List.of(row.getCell(5).toString().split(","));
                    for(String category: allowedFacilities){
                        allowedFacilityCategories.add("level " + category);
                    }
                }

                if(!StringUtil.isBlank(row.getCell(7).getStringCellValue())){
                    allowedGenders.clear();
                    allowedGenders = List.of(row.getCell(7).getStringCellValue().split(","));
                }

                intervention.setAllowedAccessCodes(allowedAccessCodes);
                intervention.setInterventionName(row.getCell(3).getStringCellValue());
                intervention.setNeedsPreAuth(row.getCell(4).getBooleanCellValue());
                intervention.setAllowedFacilityCategories(allowedFacilityCategories);
                intervention.setProviderPaymentMechanism(row.getCell(6).getStringCellValue());
                intervention.setAllowedGenders(allowedGenders);
                intervention.setMinAge((int) row.getCell(13).getNumericCellValue());
                intervention.setMaxAge((int) row.getCell(14).getNumericCellValue());
                intervention.setIsShaAuthorizationRequired(Boolean.parseBoolean(row.getCell(25).toString()));
                intervention.setIsDocAuthorizationRequired(Boolean.parseBoolean(row.getCell(24).toString()));
                intervention.setIsSurveillanceRequired(Boolean.parseBoolean(row.getCell(19).toString()));
                try {
                    intervention.setTariff((Double) row.getCell(11).getNumericCellValue());
                }catch (Exception e){
                    log.warn("Cannot convert tarrif with value {}",  row.getCell(11).toString());
                    intervention.setTariff(null);
                }
//                intervention.setHouseholdLimitCount(row.getCell(8) != null ?
//                        Integer.valueOf(row.getCell(8).getNumericCellValue()) : null);
                // Continue setting all fields in a similar manner

                interventions.add(intervention);
            }

            interventionsRepo.saveAll(interventions);
        }
    }
}
