package io.coredge.health.claims.utils;

import lombok.experimental.UtilityClass;

import java.time.*;
import java.util.Date;

@UtilityClass
public class DateUtils {

    private final ZoneId NAIROBI_ZONE = ZoneId.of("Africa/Nairobi");

    /**
     * Converts an Instant to LocalDate in Africa/Nairobi timezone.
     */
    public LocalDate toNairobiLocalDate(Instant instant) {
        return instant.atZone(NAIROBI_ZONE).toLocalDate();
    }

    /**
     * Converts an Instant to LocalDateTime in Africa/Nairobi timezone.
     */
    public LocalDateTime toNairobiLocalDateTime(Instant instant) {
        return instant.atZone(NAIROBI_ZONE).toLocalDateTime();
    }

    /**
     * Returns current LocalDate in Africa/Nairobi timezone.
     */
    public LocalDate nowNairobiLocalDate() {
        return LocalDate.now(NAIROBI_ZONE);
    }

    /**
     * Returns current LocalDateTime in Africa/Nairobi timezone.
     */
    public LocalDateTime nowNairobiDateTime() {
        return LocalDateTime.now(NAIROBI_ZONE);
    }

    public Date nowNairobiDate() {
        LocalDate todayNairobi = LocalDate.now(NAIROBI_ZONE);
        ZonedDateTime zdt = todayNairobi.atStartOfDay(NAIROBI_ZONE);
        return Date.from(zdt.toInstant());
    }

    public static LocalDateTime convertToKenyaLocalDateTime(Date date) {
        if (date == null) return null;
        return date.toInstant()
                .atZone(NAIROBI_ZONE)
                .toLocalDateTime();
    }
}
