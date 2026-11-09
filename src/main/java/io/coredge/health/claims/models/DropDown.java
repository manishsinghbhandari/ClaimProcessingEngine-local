package io.coredge.health.claims.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

@Getter
public class DropDown {
    private String code;
    private String name;

    public DropDown(String code, String name) {
        this.code = code != null ? code: null;
        this.name = name != null ? toTitleCase(name) : null;
    }

    public DropDown(String name) {
        this.code = name != null ? name.toLowerCase() : null;
        this.name = name != null ? toTitleCase(name) : null;
    }

    private String toTitleCase(String input) {
        StringBuilder result = new StringBuilder();
        boolean nextUpper = true;
        for (char c : input.toCharArray()) {
            if (Character.isSpaceChar(c) || c == '_') {
                result.append(c);
                nextUpper = true;
            } else {
                if (nextUpper) {
                    result.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    result.append(Character.toLowerCase(c));
                }
            }
        }
        return result.toString();
    }



    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DropDown that)) return false;

        return code.trim().equalsIgnoreCase(that.code.trim());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                code.trim().toLowerCase(),
                name.trim().toLowerCase()
        );
    }
}
