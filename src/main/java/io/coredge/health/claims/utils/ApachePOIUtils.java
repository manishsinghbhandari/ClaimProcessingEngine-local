package io.coredge.health.claims.utils;

import io.coredge.health.claims.models.intervention.*;
import io.coredge.health.claims.service.intervention.BusinessComponentUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
public class ApachePOIUtils {

    private ApachePOIUtils() {}

    private static final String SELECTION = "Select an Option";

    public static Map<String, Class<?>> getClassFieldNames(Class<?> modelClass) {
        Map<String , Class<?> > fieldDetails = new LinkedHashMap<>();
        for (Field field : modelClass.getDeclaredFields()) {
            fieldDetails.put(field.getName(),field.getType());
        }
        return fieldDetails;
    }

    public static String capitalizeFirstCharacter(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    public static String camelCaseCharacter(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toLowerCase() + input.substring(1);
    }

    public static CellStyle createCellStyleFromClassType(Workbook workbook, Class<?> clazz) {
        CellStyle cellStyle = workbook.createCellStyle();

        // Default font settings
        Font font = workbook.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 12);

        if (clazz == String.class) {
            // For String type: Bold and black text
            font.setBold(true);
            font.setColor(IndexedColors.BLACK.getIndex());
        } else if (Number.class.isAssignableFrom(clazz) || clazz == Integer.class || clazz == Double.class) {
            // For Numbers: Right-aligned
            cellStyle.setAlignment(HorizontalAlignment.RIGHT);
            font.setColor(IndexedColors.BLUE.getIndex());
        } else if (clazz == Boolean.class) {
            // For Booleans: Center-aligned
            cellStyle.setAlignment(HorizontalAlignment.CENTER);
            font.setColor(IndexedColors.DARK_GREEN.getIndex());
        } else if (clazz == java.util.Date.class || clazz == java.time.LocalDateTime.class) {
            // For Date/Time: Center-aligned and formatted
            cellStyle.setAlignment(HorizontalAlignment.CENTER);
            font.setColor(IndexedColors.DARK_RED.getIndex());
            DataFormat dataFormat = workbook.createDataFormat();
            cellStyle.setDataFormat(dataFormat.getFormat("yyyy-MM-dd HH:mm:ss"));
        } else {
            // Default for other types
            cellStyle.setAlignment(HorizontalAlignment.LEFT);
        }
        cellStyle.setFont(font);
        return cellStyle;
    }

    private static DataValidationConstraint getValidationType(DataValidationHelper validationHelper, Class<?> clazz , Cell cell , Cell dependentCell) {
        if (clazz == Integer.class) {
            return validationHelper.createNumericConstraint(
                    DataValidationConstraint.ValidationType.INTEGER,
                    DataValidationConstraint.OperatorType.BETWEEN,
                    String.valueOf(Integer.MIN_VALUE), String.valueOf(Integer.MAX_VALUE));
        } else if (clazz == Double.class) {
            return validationHelper.createDecimalConstraint(
                    DataValidationConstraint.OperatorType.BETWEEN,
                    String.valueOf(Double.MIN_VALUE), String.valueOf(Double.MAX_VALUE));
        } else if (clazz == Float.class) {
            return validationHelper.createDecimalConstraint(
                    DataValidationConstraint.OperatorType.BETWEEN,
                    String.valueOf(Float.MIN_VALUE), String.valueOf(Float.MAX_VALUE));
        } else if (clazz == Long.class) {
            return validationHelper.createNumericConstraint(
                    DataValidationConstraint.ValidationType.INTEGER,
                    DataValidationConstraint.OperatorType.BETWEEN,
                    String.valueOf(Long.MIN_VALUE), String.valueOf(Long.MAX_VALUE));
        } else if (clazz == Boolean.class) {
            cell.setCellValue(SELECTION);
            return validationHelper.createExplicitListConstraint(new String[]{"TRUE", "FALSE"});
        } else if (clazz == Character.class) {
            return validationHelper.createTextLengthConstraint(
                    DataValidationConstraint.OperatorType.BETWEEN, "1", "1");
        } else if (clazz == LocalDateTime.class) {
            return validationHelper.createDateConstraint(
                    DataValidationConstraint.OperatorType.BETWEEN,
                    LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), "31/12/9999", "");
        } else if (clazz == Severity.class || clazz == ResourceType.class || clazz == ComponentType.class ) {
            cell.setCellValue(SELECTION);
            return validationHelper.createExplicitListConstraint(BusinessComponentUtils.getData(clazz));
        } else if(clazz == ComponentCategory.class){
            cell.setCellValue(SELECTION);
            return validationHelper.createFormulaListConstraint("INDIRECT("+dependentCell.getAddress().formatAsString()+")");
        } else {
            return validationHelper.createTextLengthConstraint(
                    DataValidationConstraint.OperatorType.BETWEEN, "1", "255");
        }
    }

    public static void setCellValidation(Sheet sheet ,Class<?> clazz , Cell cell , Cell dependentCell) {

        DataValidationHelper validationHelper = sheet.getDataValidationHelper();
        CellRangeAddressList addressList = new CellRangeAddressList(1, 1, cell.getColumnIndex(), cell.getColumnIndex());
        DataValidationConstraint constraint = getValidationType(validationHelper, clazz , cell , dependentCell);
        DataValidation validation = validationHelper.createValidation(constraint, addressList);
        validation.createPromptBox("Input Guidelines", "Please enter a valid Value in cell");
        validation.setShowErrorBox(true);
        sheet.addValidationData(validation);
    }

    public static Object getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString(); // Convert date to string
                } else {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    return cell.getStringCellValue(); // Fallback for formula cells
                }
            case BLANK:
                return "";
            default:
                return cell.toString();
        }
    }

    public static Object getCellValueAsObject(Cell cell) {
        if (cell == null) {
            return null;
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue();
                } else {
                    return cell.getNumericCellValue();
                }
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case FORMULA:
                try {
                    return cell.getNumericCellValue();
                } catch (Exception e) {
                    return cell.getStringCellValue(); // Fallback for formula cells
                }
            case BLANK:
                return null;
            default:
                return cell.getStringCellValue();
        }
    }

    /**
     * Creates dropdown values in the Business Component Section
     * @param workbook - Workbook reference
     * @param dataSheet - reference of the Business Dropdowns sheet
     */
    public static void createDependentDropdowns(Workbook workbook, Sheet dataSheet) {
        // Prepare the data for dropdowns in the "Data" sheet
        dataSheet.setDefaultColumnWidth(50);
        List<List<String>> categories = new ArrayList<>();
        log.info("Data Sheet Name : " + dataSheet.getSheetName());

        List<ComponentType> list = List.of(ComponentType.values());
        for(ComponentType componentType : list){
            List<String> category = new ArrayList<>();
            category.add(componentType.toString());
            category.addAll(componentType.getCategories().stream().map(ComponentCategory::toString).map(String::toUpperCase).toList());
            categories.add(category);
        }

        // Populate the "Data" sheet with category data
        for (int i = 0; i < categories.size(); i++) {
            Row row = dataSheet.createRow(i);
            for (int j = 0; j < categories.get(i).size(); j++) {
                String tempRuleType = categories.get(i).get(j);
                row.createCell(j).setCellValue(tempRuleType);
                if(j==0){
                    Name range = workbook.createName();
                    range.setNameName(tempRuleType);
                    int sizePlusOne = categories.get(i).size();
                    char alphaChar = (char) ('B' + sizePlusOne);
                    range.setRefersToFormula(dataSheet.getSheetName()+"!$B$"+(i+1)+":$"+alphaChar+"$"+(i+1));
                }
            }
        }
    }
}
