package io.coredge.health.claims.db.repositories.provider;

import io.coredge.health.claims.db.entity.Provider;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ProviderSpec {

    public static Specification<Provider> filter(
            String name,
            String fidCode,
            String level,
            String county,
            String regNo
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (name != null && !name.isBlank()) {
                Expression<String> normalizedName =
                        cb.lower(cb.function("replace", String.class,
                                cb.trim(root.get("name")),
                                cb.literal("  "),
                                cb.literal(" ")
                        ));

                predicates.add(
                        cb.like(normalizedName, "%" + name.trim().toLowerCase() + "%")
                );
            }

            if (fidCode != null) {
                predicates.add(cb.equal(root.get("fidCode"), fidCode));
            }

            if (level != null) {
                predicates.add(cb.equal(cb.lower(root.get("level")), level.toLowerCase()));
            }

            if (county != null) {
                predicates.add(cb.equal(cb.lower(root.get("county")), county.toLowerCase()));
            }

            if (regNo != null) {
                predicates.add(cb.equal(cb.lower(root.get("regNo")), regNo.toLowerCase()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
