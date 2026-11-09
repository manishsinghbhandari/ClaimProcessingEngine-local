package io.coredge.health.claims.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.Getter;

import java.util.function.BiPredicate;

@Getter
public enum Operator {

    EQUALS("==", "Equals", Integer::equals),
    NOT_EQUALS("!=", "Not Equals",(a, b) -> !a.equals(b)),
    GREATER_THAN(">", "Greater Than",(a, b) -> a > b),
    LESS_THAN("<", "Less Than",(a, b) -> a < b),
    GREATER_THAN_OR_EQUAL_TO(">=", "Greater Than or Equal To", (a, b) -> a >= b),
    LESS_THAN_OR_EQUAL_TO("<=", "Less Than or Equal To", (a, b) -> a <= b);

    private String symbol;
    private String name;
    BiPredicate<Integer , Integer> predicate;

    Operator(String symbol, String name , BiPredicate<Integer , Integer> predicate) {
        this.symbol = symbol;
        this.name = name;
        this.predicate = predicate;
    }

    public static Operator fromSymbol(String symbol) {
        for (Operator op : values()) {
            if (op.symbol.equals(symbol)) return op;
        }
        throw new IllegalArgumentException("Invalid symbol: " + symbol);
    }

    @Converter(autoApply = false)
    public static class OperatorConverter implements AttributeConverter<Operator, String> {
        @Override
        public String convertToDatabaseColumn(Operator attribute) {
            return attribute != null ? attribute.getSymbol() : null;
        }

        @Override
        public Operator convertToEntityAttribute(String dbData) {
            return dbData != null ? Operator.fromSymbol(dbData) : null;
        }
    }
}