package io.coredge.health.claims.helpers;

import ca.uhn.fhir.context.FhirContext;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.models.ErrorAttribute;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Claim;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;

import java.time.format.ResolverStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author rajesh <>rajeshk@coredge.io</>
 * @apiNote Helper Class
 */
@Slf4j
@UtilityClass
public class Helper {

    /**
     * Calculates the amount based on the specified percentage.
     *
     * <p>This method takes an amount and a percentage as input, removes the '%' character
     * from the percentage string, and computes the corresponding amount based on the
     * percentage of the input amount. The result is rounded to two decimal places.</p>
     *
     * @param amount          the original amount as a BigDecimal from which the percentage will be calculated.
     * @param percentageValue the percentage of the amount to calculate, expressed as a string (e.g., "20%").
     * @return a BigDecimal representing the calculated amount based on the given percentage.
     * @throws NumberFormatException if the percentage string cannot be parsed into a valid BigDecimal.
     * @apiNote This method handles percentages represented as strings (e.g., "20%").
     * If the percentage is "0%", the method will return zero.
     */
    public BigDecimal calculateTheAmount(BigDecimal amount, BigDecimal percentageValue) {
        if (percentageValue.equals(BigDecimal.ZERO)) {
            return BigDecimal.ZERO;
        }
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        BigDecimal percentageDecimal = percentageValue.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        // Calculate the final amount
        return formatToTwoDecimalPlaces(amount.multiply(percentageDecimal));
    }

    public BigDecimal getPercentageValue(String percentage) {
        // Remove the '%' character and parse the remaining string as BigDecimal
        if (StringUtils.isBlank(percentage)) {
            return BigDecimal.ZERO;
        }
        String percentageValue = percentage.replace("%", "").trim();
        if (StringUtils.isEmpty(percentage) || percentageValue.equals("0")) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(percentageValue);
    }

    public BigDecimal formatToTwoDecimalPlaces(BigDecimal value) {
        return Objects.requireNonNullElse(value, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public Sort.Direction getSortDirection(String direction) {
        if (!direction.equalsIgnoreCase("ASC") && !direction.equalsIgnoreCase("DESC")) {
            throw new BadRequestException("Invalid Data", ErrorAttribute.builder().key("direction").value(direction).build());
        }
        return "DESC".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
    }

    //  logic to remove last code if ends with Alphabet like Level 3B to Level 3
    public static String removeLastAlphabet(String input) {
        // Use regex to check if the string ends with an alphabetic character
        if (input != null && input.matches(".*[a-zA-Z]$")) {
            // Remove the last character if it is an alphabet
            return input.substring(0, input.length() - 1);
        }
        return input; // Return original input if no changes are needed
    }

    public boolean isValidLong(String claimNumber) {
        try {
            Long.parseLong(claimNumber);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public boolean isValidUUID(String uuid) {
        try {
            UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static List<UUID> convertToUUIDList(List<String> stringList) {
        return stringList.stream().map(str -> {
            try {
                return UUID.fromString(str);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid UUID format: " + str, e);
            }
        }).collect(Collectors.toList());
    }

    public static String decodeValue(String value) {
        if (value != null) {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }
        return null;
    }

    /**
     * Gets a comparator that sorts objects based on the specified field and direction.
     *
     * @param <T> The type of the object being sorted.
     * @return A Comparator for sorting objects.
     */
    public static <T> Comparator<T> getDynamicComparator(Pageable pageable) {
        Sort.Order order = pageable.getSort().iterator().next();
        String sortBy = order.getProperty();
        boolean ascending = order.isAscending();
        return (obj1, obj2) -> {
            try {
                // Get the field from the class (or superclass if necessary)
                Field field = getFieldFromSuperClass(obj1.getClass(), sortBy);
                field.setAccessible(true); // Allow access to private fields

                // Get values of the sorting field for both objects
                Object value1 = field.get(obj1);
                Object value2 = field.get(obj2);

                // Compare null values
                int comparison = compareValues(value1, value2);

                // Reverse the comparison if the direction is descending
                return ascending ? comparison : -comparison;
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new IllegalArgumentException("Invalid sorting field: " + sortBy, e);
            }
        };
    }

    /**
     * Retrieves the field from the given class or its superclass.
     *
     * @param clazz     The class to retrieve the field from.
     * @param fieldName The name of the field.
     * @return The field object.
     * @throws NoSuchFieldException If the field is not found in the class or any superclass.
     */
    private static Field getFieldFromSuperClass(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        try {
            // Try to get the field from the class itself
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            // If not found, try to get it from the superclass
            Class<?> superclass = clazz.getSuperclass();
            if (superclass != null) {
                return getFieldFromSuperClass(superclass, fieldName);
            }
            throw e; // Throw exception if field is not found
        }
    }

    /**
     * Compares two values, handling null values first.
     *
     * @param value1 The first value.
     * @param value2 The second value.
     * @return The comparison result.
     */
    private static int compareValues(Object value1, Object value2) {
        // If both values are null, they are considered equal
        if (value1 == null && value2 == null) {
            return 0;
        }

        // If one value is null, the other is considered larger
        if (value1 == null) {
            return -1;
        }
        if (value2 == null) {
            return 1;
        }

        // Compare using Comparable if both values are non-null
        if (value1 instanceof Comparable && value2 instanceof Comparable) {
            Comparable<Object> comparableValue1 = (Comparable<Object>) value1;
            Comparable<Object> comparableValue2 = (Comparable<Object>) value2;
            return comparableValue1.compareTo(comparableValue2);
        }

        // If the values are not comparable, treat them as equal
        return 0;
    }

    /**
     * Sorts a list of objects dynamically based on the provided field name and direction,
     * then returns the result as a Page object, applying pagination.
     *
     * @param list     The list of objects to sort.
     * @param pageable The Pageable object containing pagination information.
     * @param <T>      The type of the objects being sorted.
     * @return A Page object containing the sorted and paginated list.
     */
    public static <T> Page<T> getSortedAndPaginatedList(List<T> list, Pageable pageable) {

        // Use dynamic comparator to sort based on the provided field and direction
        Comparator<T> comparator = getDynamicComparator(pageable);

        // Sort the list using the comparator
        List<T> sortedList = list.stream().sorted(comparator).collect(Collectors.toList());

        // Apply pagination to the sorted list
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), sortedList.size());
        List<T> paginatedList = sortedList.subList(start, end);

        // Return the paginated and sorted list as a Page
        return new PageImpl<>(paginatedList, pageable, sortedList.size());
    }

    /**
     * Converts a comma-separated string into a List of strings, trimming spaces from each element.
     *
     * @param commaSeparatedValues The input string with comma-separated values.
     * @return A List of strings derived from the input.
     */
    public static List<String> convertToList(String commaSeparatedValues) {
        if (!StringUtils.isNotBlank(commaSeparatedValues)) {
            return Collections.emptyList();
        }
        return Arrays.stream(commaSeparatedValues.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }


    public String getServiceDate(LocalDateTime dateTime) {
        // Define a formatter
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        // Convert LocalDateTime to String
        return dateTime.format(formatter);
    }

    public boolean isValidMobileNo(String mobileNo) {
        if (mobileNo == null || mobileNo.isEmpty()) {
            throw new IllegalArgumentException("Mobile number cannot be null or empty");
        }
        // Regex for Kenya: +254 followed by 9 digits; for India: +91 followed by 10 digits
        String regEx = "^(\\+254\\d{9}|\\+91[6-9]\\d{9})$";
        if (!mobileNo.matches(regEx)) {
            throw new IllegalArgumentException("Invalid mobile number format. Expected formats: +2547XXXXXXXX (Kenya) or +91XXXXXXXXXX (India).");
        }
        return true;
    }

    public boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty.");
        }
        String emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
        if (!email.matches(emailRegex)) {
            throw new IllegalArgumentException("Invalid email format.");
        }
        return true;
    }

    public boolean isValidPassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty.");
        }
        String passwordRegex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$";
        if (!password.matches(passwordRegex)) {
            throw new IllegalArgumentException(
                    "Invalid password. Password must be at least 8 characters long, contain an uppercase letter, a lowercase letter, a number, and a special character."
            );
        }
        return true;
    }

    /**
     * Converts a string date in the format (MM,dd,yyyy) to LocalDateTime.
     *
     * @param dateString the date string in format MM,dd,yyyy
     * @return LocalDateTime object representing the input date
     * @throws IllegalArgumentException if the input format is invalid
     */
    public static LocalDateTime convertStringToLocalDateTime(String dateString) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy");
        try {
            return LocalDateTime.of(
                    LocalDate.parse(dateString, formatter),
                    LocalTime.MIDNIGHT
            );
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format. Please use MM-dd-yyyy" + e.getMessage());
        }
    }


    public static LocalDateTime[] getStartAndEndDateTime(String monthYear) {
        try {
            if (monthYear == null || monthYear.trim().isEmpty()) {
                throw new IllegalArgumentException("Input cannot be null or empty");
            }

            // Trim input and normalize spaces
            monthYear = monthYear.trim().toUpperCase();

            // Split input into month and year
            String[] parts = monthYear.split("\\s+");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Input must be in the format 'MMM YYYY' (e.g., JAN 2025)");
            }

            String monthName = parts[0]; // Extract month part
            int year = Integer.parseInt(parts[1]); // Extract year part

            // Map of month names to numbers
            Map<String, Integer> monthMap = new HashMap<>();
            monthMap.put("JAN", 1);
            monthMap.put("FEB", 2);
            monthMap.put("MAR", 3);
            monthMap.put("APR", 4);
            monthMap.put("MAY", 5);
            monthMap.put("JUN", 6);
            monthMap.put("JUL", 7);
            monthMap.put("AUG", 8);
            monthMap.put("SEP", 9);
            monthMap.put("OCT", 10);
            monthMap.put("NOV", 11);
            monthMap.put("DEC", 12);

            if (!monthMap.containsKey(monthName)) {
                throw new IllegalArgumentException("Invalid month: " + monthName);
            }
            
            int month = monthMap.get(monthName);
            LocalDate startMonthDate = LocalDate.of(year, month, 28);
            LocalDate lastMonth = startMonthDate.minusMonths(1);
            LocalDateTime startDateTime = lastMonth.plusDays(1).atTime(0, 0, 0);
            // End date: must be 28th of every month.
            LocalDate endMonthDate = LocalDate.of(year, month, 28);
            LocalDateTime endDateTime = endMonthDate.atTime(23, 59, 59);

            log.info("StartDate : {} and EndDate : {}", startDateTime, endDateTime);
            return new LocalDateTime[]{startDateTime, endDateTime};
        } catch (Exception e) {
            System.out.println("Parsing error: " + e.getMessage());
            throw new IllegalArgumentException("Input must be in the format 'MMM YYYY' (e.g., JAN 2025)", e);
        }
    }

    public LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return LocalDateTime.parse(dateTimeStr, formatter);
    }

    public Sort createSortObject(String sortBy, String direction , boolean rootIsClaimLineItem ) {
        if (sortBy == null || sortBy.isEmpty()) {
            return Sort.by(rootIsClaimLineItem ? "claim.dateFrom" : "createDate").descending();
        }

        String[] sortFields = sortBy.split(",");
        String[] directions = (direction != null) ? direction.split(",") : new String[0];

        List<Sort.Order> orders = new ArrayList<>();

        for (int i = 0; i < sortFields.length; i++) {
            String field = mapSortField(sortFields[i].trim(), rootIsClaimLineItem);

            Sort.Direction dir = Sort.Direction.ASC;
            if (i < directions.length) {
                dir = getSortDirection(directions[i].trim());
            }

            orders.add(new Sort.Order(dir, field));
        }

        return Sort.by(orders);
    }

    public static String mapSortField(String field, boolean rootIsClaimLineItem) {
        switch (field.toLowerCase()) {
            case "providername":
                return rootIsClaimLineItem ? "claim.provider.name" : "ic.provider.name";
            case "patientname":
                return rootIsClaimLineItem ? "claim.patient.name" : "ic.patient.name";
            case "totalamount":
                return rootIsClaimLineItem ? "totalAmount" : "ic.totalAmount";
            case "claimnumber":
                return rootIsClaimLineItem ? "claim.claimNumber" : "ic.claimNumber";
            case "createdate":
                return rootIsClaimLineItem ? "claim.claimCreatedAt" : "ic.claimCreatedAt";
            case "shacode":
                return rootIsClaimLineItem ? "claim.shaCode" : "ic.shaCode";
            case "shaname":
                return rootIsClaimLineItem ? "interventionName" : "ic.interventionName";
            case "servicedate":
                return rootIsClaimLineItem ? "servicedDate" : "servicedDate";
            case "claimtype":
                return rootIsClaimLineItem ? "claim.use" : "ic.use";
            case "amount":
                return rootIsClaimLineItem ? "netValue" : "ic.netValue";
            case "accesscode":
                return rootIsClaimLineItem ? "claim.accessCode" : "ic.accessCode";
            case "gender":
                return rootIsClaimLineItem ? "gender" : "ic.gender";
            case "birthdate":
                return rootIsClaimLineItem ? "birth_date" : "ic.birth_date";
            case "crid":
                return rootIsClaimLineItem ? "cr_id" : "ic.cr_id";
            case "fidcode":
                return rootIsClaimLineItem ? "fidCode" : "ic.provider.fidCode";
            case "status":
                return rootIsClaimLineItem ? "claim.status" : "ic.status";
            case "lastupdated":
                return rootIsClaimLineItem ? "last_updated" : "ic.last_updated";
            default:
                return rootIsClaimLineItem ? field : "ic." + field;
        }
    }

    public boolean isNumeric(String input) {
        return input.matches("\\d+");
    }

    public void validateInputs(String claimNumber, String nationalId) {
        if (claimNumber != null && !(Helper.isValidUUID(claimNumber) || Helper.isNumeric(claimNumber))) {
            throw new IllegalArgumentException("Invalid claimNumber: must be a UUID or numeric — " + claimNumber);
        }
        if (nationalId != null && !Helper.isNumeric(nationalId)) {
            throw new IllegalArgumentException("Invalid National ID format: " + nationalId);
        }
    }

    public static LocalDateTime getLastDateTimeOfMonth(String monthYear) {
        DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern("MMM yyyy")
                .toFormatter(Locale.ENGLISH)
                .withResolverStyle(ResolverStyle.SMART);

        YearMonth yearMonth = YearMonth.parse(monthYear, formatter);
        return yearMonth.atEndOfMonth().atTime(0, 1, 1);
    }

}
