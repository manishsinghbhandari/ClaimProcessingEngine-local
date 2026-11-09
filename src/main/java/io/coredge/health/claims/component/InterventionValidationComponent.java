package io.coredge.health.claims.component;

import io.coredge.health.claims.utils.FhirHelper;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Period;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.function.BiPredicate;

@Slf4j
@Component
public class InterventionValidationComponent {

    public boolean existCode(String requestCode , List<String> codes) {
        return codes.stream().anyMatch(code -> requestCode.toLowerCase().contains(code.toLowerCase()));
    }

    public boolean validatePeriod(int allowedDays , BiPredicate<Integer , Integer> predicate, Date previousStartDate , Date startDate) {
        int days = FhirHelper.calculateLoS(previousStartDate, startDate);
        return predicate.test(days , allowedDays);
    }
}

