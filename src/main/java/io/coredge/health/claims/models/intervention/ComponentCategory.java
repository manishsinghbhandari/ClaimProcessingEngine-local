package io.coredge.health.claims.models.intervention;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.BiPredicate;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
public enum ComponentCategory {
  RANGE_NUMBER(
      "Range",
      Double.class,
      Range.class,
      (v1, v2) -> {
        double input = (Double) v1;
        double min = (Double) convertStringToObject(Double.class, ((Range) v2).getMin());
        double max = (Double) convertStringToObject(Double.class, ((Range) v2).getMax());
        return min <= input && input <= max;
      }),
  RANGE_DATE(
      "Range",
      LocalDate.class,
      Range.class,
      (v1, v2) -> {
        LocalDate input = (LocalDate) v1;
        LocalDate min = (LocalDate) convertStringToObject(LocalDate.class, ((Range) v2).getMin());
        LocalDate max = (LocalDate) convertStringToObject(LocalDate.class, ((Range) v2).getMax());
        return input.equals(min) || input.equals(max) || input.isAfter(min) && input.isBefore(max);
      }),
  RANGE_DATE_TIME(
      "Range",
      LocalDateTime.class,
      Range.class,
      (v1, v2) -> {
        LocalDateTime input = (LocalDateTime) v1;
        LocalDateTime min =
            (LocalDateTime) convertStringToObject(LocalDateTime.class, ((Range) v2).getMin());
        LocalDateTime max =
            (LocalDateTime) convertStringToObject(LocalDateTime.class, ((Range) v2).getMax());
        return input.equals(min) || input.equals(max) || input.isAfter(min) && input.isBefore(max);
      }),
  EQUAL_STRING(
      "Equal",
      String.class,
      Value.class,
      (v1, v2) -> StringUtils.equals((String) v1, ((Value) v2).getValue())),
  EQUAL_NUMBER(
      "Equal",
      Double.class,
      Value.class,
      (v1, v2) -> v1.equals(convertStringToObject(Double.class, ((Value) v2).getValue()))),
  EQUAL_BOOLEAN(
      "Equal",
      Boolean.class,
      Value.class,
      (v1, v2) -> v1.equals(convertStringToObject(Boolean.class, ((Value) v2).getValue()))),
  EQUAL_DATE(
      "Equal",
      LocalDate.class,
      Value.class,
      (v1, v2) -> v1.equals(convertStringToObject(LocalDate.class, ((Value) v2).getValue()))),
  EQUAL_DATE_TIME(
      "Equal",
      LocalDateTime.class,
      Value.class,
      (v1, v2) -> v1.equals(convertStringToObject(LocalDateTime.class, ((Value) v2).getValue()))),
  NOT_EQUAL_STRING(
      "NotEqual",
      String.class,
      Value.class,
      (v1, v2) -> !StringUtils.equals((String) v1, ((Value) v2).getValue())),
  NOT_EQUAL_NUMBER(
      "NotEqual",
      Double.class,
      Value.class,
      (v1, v2) -> !(v1.equals(convertStringToObject(Double.class, ((Value) v2).getValue())))),
  NOT_EQUAL_BOOLEAN(
      "NotEqual",
      Boolean.class,
      Value.class,
      (v1, v2) -> !(v1.equals(convertStringToObject(Boolean.class, ((Value) v2).getValue())))),
  NOT_EQUAL_DATE(
      "NotEqual",
      LocalDate.class,
      Value.class,
      (v1, v2) -> !(v1.equals(convertStringToObject(LocalDate.class, ((Value) v2).getValue())))),
  NOT_EQUAL_DATE_TIME(
      "NotEqual",
      LocalDateTime.class,
      Value.class,
      (v1, v2) ->
          !(v1.equals(convertStringToObject(LocalDateTime.class, ((Value) v2).getValue())))),
  CONTAIN_STRING(
      "Contain",
      String.class,
      Value.class,
      (v1, v2) -> StringUtils.contains((String) v1, ((Value) v2).getValue())),
  EQUAL_IGNORE_CASE(
      "EqualIgnoreCase",
      String.class,
      Value.class,
      (v1, v2) -> StringUtils.equalsIgnoreCase((String) v1, ((Value) v2).getValue())),
  ANY_MATCH(
      "AnyMatch",
      String.class,
      Value.class,
      (v1, v2) -> {
        return ((List<String>) convertStringToObject(List.class, ((Value) v2).getValue()))
            .stream().anyMatch(value -> value.equalsIgnoreCase((String) v1));
      }),
  ALL_MATCH(
      "AllMatch",
          ((Class)List.class),
      Value.class,
      (v1, v2) ->{
             List<String> rules = ((List<String>) convertStringToObject(List.class, ((Value) v2).getValue()));
             List<String> inputs = (List<String>) v1;
        return inputs.stream().allMatch(rules::contains);
      }),
  NON_MATCH(
      "NonMatch",
      String.class,
      Value.class,
      (v1, v2) ->
              ((List<String>) convertStringToObject(List.class, ((Value) v2).getValue()))
                      .stream().noneMatch(value -> value.equalsIgnoreCase((String) v1))),
  CONTAIN_LIST(
          "Contain",
          String.class,
          Value.class,
          (v1, v2) -> ((List<String>) convertStringToObject(List.class, ((Value) v2).getValue()))
                  .contains(v1)),
  ;

  private String category;
  private Class inputType;
  private Class ruleObject;
  private BiPredicate<Object, Object> predicate;

  public static final String DATE_FORMAT = "yyyy-MM-dd";
  public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

  ComponentCategory(
      String category, Class inputType, Class ruleObject, BiPredicate<Object, Object> predicate) {
    this.category = category;
    this.inputType = inputType;
    this.ruleObject = ruleObject;
    this.predicate = predicate;
  }

  public static Object convertStringToObject(Class<?> clazz, String input) {
    if (clazz == Integer.class) {
      return Integer.valueOf(input); // Casting String to Integer
    } else if (clazz == Double.class) {
      return Double.valueOf(input); // Casting String to Double
    } else if (clazz == Boolean.class) {
      return Boolean.valueOf(input); // Casting String to Boolean
    } else if (clazz == Long.class) {
      return Long.valueOf(input); // Casting String to Long
    } else if (clazz == Float.class) {
      return Float.valueOf(input); // Casting String to Float
    } else if (clazz == Short.class) {
      return Short.valueOf(input); // Casting String to Short
    } else if (clazz == Byte.class) {
      return Byte.valueOf(input); // Casting String to Byte
    } else if (clazz == LocalDate.class) {
      return LocalDate.parse(input, DateTimeFormatter.ofPattern(DATE_FORMAT));
    } else if (clazz == LocalDateTime.class) {
      return LocalDateTime.parse(input, DateTimeFormatter.ofPattern(DATE_TIME_FORMAT));
    } else if (clazz == List.class) {
      try {
        ObjectMapper objectMapper = new ObjectMapper();
        TypeReference<List<String>> jacksonTypeReference = new TypeReference<List<String>>() {};
        return objectMapper.readValue(input, jacksonTypeReference);
      } catch (Exception e) {
        return List.of();
      }
    } else if (clazz == Value.class) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(input, Range.class);
        } catch (Exception e) {
            return List.of();
        }
    } else if (clazz == Range.class) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
           return objectMapper.readValue(input, Value.class );
        } catch (Exception e) {
            return List.of();
        }
    } else if (clazz == String.class) {
      return input; // If it's a String, just return the original input
    }

    // If class is not in the predefined list, you can attempt to instantiate or handle other types
    throw new IllegalArgumentException("Unsupported class type: " + clazz.getName());
  }

  public static boolean validateRule(ComponentCategory category, Object input, Object rule) {
    return category.predicate.test(input, rule);
  }

  @JsonCreator
  public static ComponentCategory fromValue(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    return ComponentCategory.valueOf(value.toUpperCase());
  }
}
