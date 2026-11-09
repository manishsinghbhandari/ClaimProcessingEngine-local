package io.coredge.health.claims.dto.facility;


public record BedOccupancyDto(
        Long providerId,
        int totalBeds,
        int normalBeds,
        int icuBeds,
        int hduBeds,
        int dialysisBeds,
        int numberOfCots
) {}