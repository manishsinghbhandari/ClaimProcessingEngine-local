package io.coredge.health.claims.models.phccalculation;

import java.util.List;

public record CountyStatsResponse(List<CountyStatDto> data) {
}