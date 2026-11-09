package io.coredge.health.claims.service.phc;

import io.coredge.health.claims.db.entity.CountyPopulation;
import io.coredge.health.claims.models.phccalculation.CountyStatDto;

import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.Locale;

public class CountyPopulationHelper {

    private CountyPopulationHelper(){
    }

    /**
     * Maps a {@link CountyStatDto} received from the external PHC API to a {@link CountyPopulation}
     * entity suitable for persistence in MongoDB.
     * <p>
     * Specifically, it:
     * <ul>
     *   <li>Extracts and formats the {@code YEAR_MONTH} field (e.g., {@code 202505}) to a readable
     *       month-year string like {@code "May 2025"}.</li>
     *   <li>Creates a new {@link CountyPopulation} instance with null ID (Mongo will auto-generate it),
     *       the county name, population, and formatted month.</li>
     * </ul>
     *
     * @param dto the {@link CountyStatDto} containing county population data
     * @return a mapped {@link CountyPopulation} entity ready for database storage
     */
    public static CountyPopulation mapToCountyPopulation(CountyStatDto dto) {
        // Convert YEAR_MONTH (e.g. 202505) → "May 2025"
        String yearMonth = dto.yearMonth().toString(); // "202505"
        int year = Integer.parseInt(yearMonth.substring(0, 4));
        int month = Integer.parseInt(yearMonth.substring(4));

        String formattedMonth = Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year;

        return new CountyPopulation(
                null,                          // let Mongo generate the ID
                dto.county(),
                dto.registrations(),
                formattedMonth
        );
    }

    /**
     * Converts a human-readable month name (e.g., {@code "May 2025"}) into a numeric
     * {@code YEAR_MONTH} format (e.g., {@code "202505"}).
     * <p>
     * This is useful for aligning input strings with the API's or database's expected
     * {@code YEAR_MONTH} format.
     *
     * @param monthName the month name string in the format {@code "MMMM yyyy"}, e.g., {@code "June 2025"}
     * @return a numeric string in {@code "yyyyMM"} format, e.g., {@code "202506"}
     * @throws DateTimeParseException if the input does not match the expected format
     */
    public static String convertMonthNameToYearMonth(String monthName) {
        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
        YearMonth yearMonth = YearMonth.parse(monthName, inputFormatter);
        return String.format("%d%02d", yearMonth.getYear(), yearMonth.getMonthValue());
    }
}
