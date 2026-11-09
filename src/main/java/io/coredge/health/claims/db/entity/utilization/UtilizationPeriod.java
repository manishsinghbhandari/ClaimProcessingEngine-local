package io.coredge.health.claims.db.entity.utilization;

import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.function.Function;

@Getter
public enum UtilizationPeriod {
    DAILY("Daily",
            (LocalDate date) -> date.plusDays(1) ,
            (LocalDate date) -> date,
            (LocalDate date) -> date
    ),
    WEEKLY("Weekly",
            (LocalDate date) -> date.with(TemporalAdjusters.next(DayOfWeek.MONDAY)),
            (LocalDate date) ->  date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
            (LocalDate date) -> date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    ),
    MONTHLY("Monthly",
            (LocalDate date) -> date.plusMonths(1),
            (LocalDate date) -> date.minusMonths(1),
            (LocalDate date) -> date
    ),
    QUARTERLY("Quarterly",
            (LocalDate date) -> date.plusMonths(3),
            (LocalDate date) -> date.minusMonths(3),
            (LocalDate date) -> date
    ),
    MATERNITY("Maternity",
            (LocalDate date) -> date.plusMonths(10),
            (LocalDate date) -> date.minusMonths(10),
            (LocalDate date) -> date
    ),
    YEARLY("Yearly",
            (LocalDate date) -> date.plusYears(1),
            (LocalDate date) -> date.minusYears(1),
            (LocalDate date) -> date
    ),
    TWO_YEARLY("Two Yearly",
            (LocalDate date) -> date.plusYears(2),
            (LocalDate date) -> date.minusYears(2),
            (LocalDate date) -> date
    ),
    NO_LIMIT("No Limit",
            (LocalDate date) -> date.plusYears(999),
            (LocalDate date) -> date.minusYears(999),
            (LocalDate date) -> date
            );

    private final String name;
    private final Function<LocalDate, LocalDate> nextAvailableDate;
    private final Function<LocalDate, LocalDate> periodStartDate;
    private final Function<LocalDate, LocalDate> periodEndDate;

    UtilizationPeriod(String name ,
                      Function<LocalDate, LocalDate> nextAvailableDate,
                      Function<LocalDate, LocalDate> periodStartDate,
                      Function<LocalDate, LocalDate> periodEndDate) {
        this.name = name;
        this.nextAvailableDate = nextAvailableDate;
        this.periodStartDate = periodStartDate;
        this.periodEndDate = periodEndDate;
    }

    public static LocalDate computeNextAvailableDate(Boolean eligibility , LocalDate lastUsageDate  , LocalDate policyEndDate , UtilizationPeriod period) {
        if(UtilizationPeriod.YEARLY.equals(period)) {
            if(eligibility) {
                return LocalDate.now();
            }
            else {
                return policyEndDate.plusDays(1);
            }
        }
        else  {
            if(eligibility) {
                return LocalDate.now();
            }
            else{
                return period.nextAvailableDate.apply(lastUsageDate);
            }
        }
    }

    public static LocalDate getStartOfPeriod(UtilizationPeriod utilizationPeriod){
        return getStartOfPeriod(utilizationPeriod , LocalDate.now());
    }

    public static LocalDate getEndOfPeriod(UtilizationPeriod utilizationPeriod){
        return getEndOfPeriod(utilizationPeriod , LocalDate.now());
    }

    public static LocalDate getStartOfPeriod(UtilizationPeriod utilizationPeriod , LocalDate date){
        return utilizationPeriod.periodStartDate.apply(date);
    }

    public static LocalDate getEndOfPeriod(UtilizationPeriod utilizationPeriod , LocalDate date){
        return utilizationPeriod.periodEndDate.apply(date);
    }
}
