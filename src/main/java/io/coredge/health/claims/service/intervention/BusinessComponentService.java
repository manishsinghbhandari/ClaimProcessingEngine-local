package io.coredge.health.claims.service.intervention;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.db.entity.intervention.BusinessComponent;
import io.coredge.health.claims.db.repositories.intervention.BusinessComponentRepository;
import io.coredge.health.claims.exception.app.DataNotFoundException;
import io.coredge.health.claims.mapper.InterventionMapper;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.models.intervention.ComponentCategory;
import io.coredge.health.claims.models.intervention.ComponentType;
import io.coredge.health.claims.models.intervention.request.BusinessComponentRequest;
import io.coredge.health.claims.models.intervention.response.BusinessComponentResponse;
import io.coredge.health.claims.utils.ApachePOIUtils;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static io.coredge.health.claims.utils.ApachePOIUtils.*;

@Service
@Slf4j
public class BusinessComponentService {

    private final BusinessComponentRepository repository;
    private final InterventionMapper ruleMapper;
    private final ObjectMapper objectMapper;

    @Autowired
    public BusinessComponentService(BusinessComponentRepository repository, InterventionMapper ruleMapper, ObjectMapper objectMapper) {
        this.repository = repository;
        this.ruleMapper = ruleMapper;
        this.objectMapper = objectMapper;
    }

    public BusinessComponentResponse addBusinessComponent(BusinessComponentRequest request) throws InvocationTargetException, IllegalAccessException {

        ComponentCategory.convertStringToObject(request.getComponentCategory().getRuleObject() ,request.getValue());
        BusinessComponent component = new BusinessComponent();
        ruleMapper.copyProperties(request, component);
        component.setCreatedBy(HealthIdContextHolder.userId());
        BusinessComponent response = repository.save(component);
        BusinessComponentResponse componentResponse = new BusinessComponentResponse();
        ruleMapper.copyProperties(response, componentResponse);
        return componentResponse;
    }

    public BusinessComponentResponse getBusinessComponent(Long id) {
        Optional<BusinessComponent> componentOptional = repository.findByIdAndIsDeletedFalse(id);
        if (componentOptional.isEmpty()) {
            throw new DataNotFoundException("Business component not found with the id " + id);
        }
        return ruleMapper.toBusinessComponentResponse(componentOptional.get());
    }

    public List<BusinessComponentResponse> getBusinessComponent(String code) {
        List<BusinessComponent> businessComponentResponseList =  repository.findByCodeAndIsDeletedIsFalse(code);
        return ruleMapper.toBusinessComponentResponse(businessComponentResponseList);
    }

    public List<BusinessComponentResponse> getBusinessComponent() {
        List<BusinessComponent> businessComponentResponseList = repository.findByIsDeletedFalse();
        return ruleMapper.toBusinessComponentResponse(businessComponentResponseList);
    }

    public PageDropDown getPageDropDown() {
        return new PageDropDown();
    }

    public Map<String, Long> getBusinessComponentDropDown() {
        List<BusinessComponent> components = repository.findByIsDeletedFalse();
        return components.stream().collect(Collectors.toMap(BusinessComponent::getName , BusinessComponent::getId));
    }

    public List<String> getBusinessComponentFilterDropDown() {
        List<BusinessComponent> components = repository.findByIsDeletedFalse();
        return components.stream().map(BusinessComponent::getCode).distinct().toList();
    }

    public Map<String, Long> getInterventionDropDown(String code) {
        List<BusinessComponent> components = repository.findByCodeAndIsDeletedIsFalse(code);
        return components.stream().collect(Collectors.toMap(BusinessComponent::getName , BusinessComponent::getId));
    }

    public BusinessComponentResponse updateBusinessComponent(BusinessComponentRequest request, Long id) throws InvocationTargetException, IllegalAccessException {
        ComponentCategory.convertStringToObject(request.getComponentCategory().getRuleObject() ,request.getValue());
        Optional<BusinessComponent> previousRule = repository.findByIdAndIsDeletedFalse(id);
        if (previousRule.isPresent()) {
            BusinessComponent previousComponent = new BusinessComponent();
            BusinessComponent currentComponent = new BusinessComponent();
            ruleMapper.copyProperties(previousRule.get(), previousComponent);
            ruleMapper.copyProperties(previousRule.get(), currentComponent);
            ruleMapper.copyProperties(request, currentComponent);
            currentComponent.setVersion(previousComponent.getVersion() + 1);
            currentComponent.setLastModifiedBy(HealthIdContextHolder.userId());
            currentComponent.setLastModifiedDate(LocalDateTime.now());
            BusinessComponent response = repository.save(currentComponent);
            previousComponent.setId(null);
            previousComponent.setIsDeleted(true);
            previousComponent.setLastModifiedBy(HealthIdContextHolder.userId());
            previousComponent.setLastModifiedDate(LocalDateTime.now());
            repository.save(previousComponent);
            BusinessComponentResponse copiedBusinessComponent = new BusinessComponentResponse();
            ruleMapper.copyProperties(response, copiedBusinessComponent);
            return copiedBusinessComponent;
        } else {
            throw new BadRequestException("No Previous Entry of Intervention Rule found if you still need please create a new one");
        }
    }

    @Transactional
    public void updateBusinessComponentRuleStatus(Long id, boolean status) throws InvocationTargetException, IllegalAccessException {
        Optional<BusinessComponent> previousRule = repository.findByIdAndIsDeletedFalse(id);
        if (previousRule.isPresent()) {
            BusinessComponent previousComponent = new BusinessComponent();
            BusinessComponent currentComponent = new BusinessComponent();
            ruleMapper.copyProperties(previousRule.get(), previousComponent);
            ruleMapper.copyProperties(previousRule.get(), currentComponent);
            previousComponent.setIsDeleted(true);
            previousComponent.setId(null);
            previousComponent.setLastModifiedBy(HealthIdContextHolder.userId());
            previousComponent.setLastModifiedDate(LocalDateTime.now());
            repository.save(previousComponent);
            currentComponent.setLastModifiedBy(HealthIdContextHolder.userId());
            currentComponent.setLastModifiedDate(LocalDateTime.now());
            currentComponent.setIsActive(status);
            currentComponent.setVersion(currentComponent.getVersion() + 1);
            repository.save(currentComponent);
        } else {
            throw new BadRequestException("Error in getting BusinessComponent for the given id:" + id);
        }
    }

    public void deleteBusinessComponentRule(Long id) {
        repository.markDelete(id);
    }

    public byte[] downloadFile() {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Business Components");
            Sheet dropDowns = workbook.createSheet("Data");

            ApachePOIUtils.createDependentDropdowns(workbook, dropDowns);

            sheet.setDefaultColumnWidth(20);

            // Create header row
            Row headerRow = sheet.createRow(0);
            Row dataRow = sheet.createRow(1);
            CellStyle headerStyle = workbook.createCellStyle();

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontName("Arial");
            headerFont.setFontHeightInPoints((short) 12);
            headerFont.setColor(IndexedColors.BLACK.getIndex());
            headerStyle.setFont(headerFont);
            Set<Map.Entry<String, Class<?>>> headers = ApachePOIUtils.getClassFieldNames(BusinessComponentRequest.class).entrySet();
            AtomicInteger cellIndex = new AtomicInteger();
            AtomicReference<Cell> ruleTypeCell = new AtomicReference<>();
            headers.forEach(header -> {
                Cell cell0 = headerRow.createCell(cellIndex.get());
                cell0.setCellStyle(headerStyle);
                cell0.setCellValue(ApachePOIUtils.capitalizeFirstCharacter(header.getKey()));
                Cell cell1 = dataRow.createCell(cellIndex.get());
                cell1.setCellStyle(ApachePOIUtils.createCellStyleFromClassType(workbook, header.getValue()));
                if (header.getValue() == ComponentCategory.class) {
                    ApachePOIUtils.setCellValidation(sheet, header.getValue(), cell1, ruleTypeCell.get());
                } else {
                    ApachePOIUtils.setCellValidation(sheet, header.getValue(), cell1, null);
                }

                if (header.getValue() == ComponentType.class) {
                    ruleTypeCell.set(cell1);
                }
                cellIndex.getAndIncrement();
            });

            // Write the workbook to a byte array
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                workbook.write(outputStream);
                return outputStream.toByteArray();
            }
        } catch (Exception e) {
            throw new BadRequestException("Failed to generate Excel file: " + e.getMessage());
        }
    }

    public List<String> uploadComponents(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty.please upload a valid file.");
        }
        try (InputStream inputStream = file.getInputStream() ; Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);

            List<String> headers = new ArrayList<>();
            List<BusinessComponentRequest> entities = new ArrayList<>();

            for (Cell cell : sheet.getRow(0)) {
                headers.add(camelCaseCharacter(cell.getStringCellValue().trim()));
            }
            sheet.removeRow(sheet.getRow(0));


            for (Row row : sheet) {
                Map<String, Object> records = new HashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = row.getCell(i);
                    records.put(headers.get(i), getCellValueAsObject(cell));
                }
                BusinessComponentRequest entity =  objectMapper.convertValue(records, BusinessComponentRequest.class);
                entities.add(entity);
            }
            return saveRecords(entities);
        } catch (Exception e) {
            throw new BadRequestException("Error processing file: " + e.getMessage());
        }
    }

    private List<String> saveRecords(List<BusinessComponentRequest> requests){
        List<String> errors = new ArrayList<>();
        for(int i = 0 ; i < requests.size() ; i++){
            try {
                    addBusinessComponent(requests.get(i));
                }catch (Exception e){
                    errors.add(String.format("Error in storing value at line number :[%s] with error :[%s]",i+2 , e.getMessage() ));
                }
            }
        return errors;
    }
}
