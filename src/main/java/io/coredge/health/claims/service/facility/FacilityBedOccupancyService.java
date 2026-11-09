package io.coredge.health.claims.service.facility;

import ca.uhn.fhir.context.FhirContext;
import io.coredge.health.claims.constants.BedOccupancyConstants;
import io.coredge.health.claims.db.entity.Provider;
import io.coredge.health.claims.db.entity.facility.FacilityBedCapacity;
import io.coredge.health.claims.db.entity.facility.FacilityBedOccupancy;
import io.coredge.health.claims.db.entity.facility.FacilityBedOccupancyClaim;
import io.coredge.health.claims.db.repositories.BenefitsMasterRepository;
import io.coredge.health.claims.db.repositories.ProviderRepository;
import io.coredge.health.claims.db.repositories.facility.FacilityBedCapacityRepository;
import io.coredge.health.claims.db.repositories.facility.FacilityBedOccupancyRepository;
import io.coredge.health.claims.dto.facility.BedOccupancyDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Period;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class FacilityBedOccupancyService {

    private final FacilityBedOccupancyRepository occupancyRepository;
    private final FacilityBedCapacityRepository capacityRepository;
    private final BenefitsMasterRepository benefitsRepository;

    private final FhirContext fhirContext;

    private final ProviderRepository providerRepository;

    public List<BedOccupancyDto> extractBedOccupancy(String hieProviderJson) {

        Organization organization = fhirContext.newJsonParser().parseResource(Organization.class, hieProviderJson);
        log.info("Organization Id: {}", organization.getIdElement().getIdPart());

        String frCode = Optional.ofNullable(organization.getIdElement()).map(IdType::getIdPart).orElse(null);

        Optional<List<Provider>> providerDetails = providerRepository.findByFidCode(frCode);

        Long providerId = providerDetails.map(providers -> providers.getFirst().getId()).orElse(null);
        // Find the named 'bed-occupancy' extension
        Optional<Extension> bedOccupancyExtension = organization.getExtension()
                .stream().filter(ext -> BedOccupancyConstants.FHIR_BED_OCCUPANCY
                        .equals(ext.getUrl())).findFirst();

        return bedOccupancyExtension.map(ext -> mapToBedOccupancyDto(ext.getExtension(), providerId)).map(List::of).orElseGet(List::of);
    }

    private BedOccupancyDto mapToBedOccupancyDto(List<Extension> bedExtensions, Long providerId) {
        Map<String, Integer> values = bedExtensions.stream().filter(Objects::nonNull).collect(HashMap::new, (map, ext) -> {
            String key = extractKey(ext.getUrl());
            int value = extractIntValue(ext);
            if (key != null) map.put(key, value);
        }, HashMap::putAll);

        return new BedOccupancyDto(providerId, values.getOrDefault(BedOccupancyConstants.TOTAL_BEDS,
                BedOccupancyConstants.ZERO),
                values.getOrDefault(BedOccupancyConstants.NORMAL_BEDS,
                        BedOccupancyConstants.ZERO),
                values.getOrDefault(BedOccupancyConstants.ICU_BEDS,
                        BedOccupancyConstants.ZERO),
                values.getOrDefault(BedOccupancyConstants.HDU_BEDS,
                        BedOccupancyConstants.ZERO),
                values.getOrDefault(BedOccupancyConstants.DIALYSIS_BEDS,
                        BedOccupancyConstants.ZERO),
                values.getOrDefault(BedOccupancyConstants.NUM_OF_COTS,
                        BedOccupancyConstants.ZERO));
    }

    private String extractKey(String url) {
        return (url != null && !url.isBlank()) ? url.substring(url.lastIndexOf('/') + BedOccupancyConstants.ONE) : null;
    }

    private int extractIntValue(Extension ext) {
        return (ext.getValue() instanceof IntegerType it) ? it.getValue() : BedOccupancyConstants.ZERO;
    }


    public void updateBedOccupancyForClaimItem(Claim claim, Provider provider) {
        log.info("Processing claim ID: {}", claim.getIdPart());

        if (!Claim.Use.CLAIM.equals(claim.getUse())) {
            log.info("Skipping occupancy update for use type: {}", claim.getUse());
            return;
        }

        List<String> bedTypes = new ArrayList<>();
        FacilityBedCapacity facilityBedCapacity = capacityRepository.findByProviderId(provider.getId());
        log.info("FacilityBedCapacityProviderId: {}", facilityBedCapacity.getProviderId());


        List<LocalDate> dates = new ArrayList<>();

        for (Claim.ItemComponent item : claim.getItem()) {
            String bedType = extractBedTypes(item);
            Optional<Period> periodOpt = extractServicedPeriod(item);
            Period period = periodOpt.orElse(null);
            LocalDate start = period != null ? toLocalDate(period.getStart()) : null;
            LocalDate end = period != null ? toLocalDate(period.getEnd()) : null;

            // Unified condition to skip invalid entries
            if (period == null || isInvalidDateRange(start, end)) {
                log.debug("Skipping item due to missing or invalid data: period={}, start={}, end={} ", period, start, end);
                continue;
            }
            start.datesUntil(end).forEach(dates::add);
            bedTypes.add(bedType);
        }
        List<FacilityBedOccupancy> updatedOccupancies = getOrCreateOccupancy(provider, bedTypes, dates, claim.getIdPart(), facilityBedCapacity);
        occupancyRepository.saveAll(updatedOccupancies);
        log.info("Saved {} occupancy records for claim ID: {}", updatedOccupancies.size(), claim.getIdPart());
    }


    private boolean isInvalidDateRange(LocalDate start, LocalDate end) {
        return start == null || end == null || !start.isBefore(end);
    }

    private String extractBedTypes(Claim.ItemComponent item) {
        String interventionCode = item.getProductOrService().getCodingFirstRep().getCode();
        log.info("Extracting bed types for intervention code: {}", interventionCode);
        return benefitsRepository.findByInterventionCode(interventionCode)
                .filter(list -> !list.isEmpty())
                .map(list -> list.getFirst()
                        .getBedType()).orElse(StringUtils.EMPTY);
    }


    private Optional<Period> extractServicedPeriod(Claim.ItemComponent item) {
        return item.getServiced() instanceof Period period ? Optional.of(period) : Optional.empty();
    }

    private LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private List<FacilityBedOccupancy> getOrCreateOccupancy(Provider provider, List<String> bedTypes, List<LocalDate> dates, String claimId, FacilityBedCapacity facilityBedCapacity) {
        List<FacilityBedOccupancy> occupancies = occupancyRepository.findByProviderAndBedTypeInAndOccupancyDateIn(provider, bedTypes, dates);

        if (occupancies.isEmpty()) {
            occupancies = createNewOccupancies(provider, bedTypes, dates);
        }

        occupancies.forEach(occupancy -> populateOccupancyFromCapacity(occupancy, facilityBedCapacity, claimId));

        return occupancies;
    }

    private void addClaimIfAbsent(FacilityBedOccupancy occupancy, String claimId) {
        if (CollectionUtils.isEmpty(occupancy.getFacilityBedOccupancyClaims())) {
            occupancy.setFacilityBedOccupancyClaims(new HashSet<>());
        }
        boolean alreadyPresent = occupancy.getFacilityBedOccupancyClaims().stream()
                .anyMatch(c -> c.getClaimId().toString().equals(claimId));

        if (!alreadyPresent) {
            FacilityBedOccupancyClaim facilityBedOccupancyClaim = new FacilityBedOccupancyClaim();
            facilityBedOccupancyClaim.setClaimId(UUID.fromString(claimId));
            facilityBedOccupancyClaim.setFacilityBedOccupancy(occupancy);
            occupancy.getFacilityBedOccupancyClaims().add(facilityBedOccupancyClaim);  // Important: bidirectional mapping
        }
    }

    private List<FacilityBedOccupancy> createNewOccupancies(Provider provider, List<String> bedTypes, List<LocalDate> dates) {
        log.info("Creating new occupancy records for provider={}, bedTypes={}, dates={}", provider.getId(), bedTypes, dates);

        List<FacilityBedOccupancy> newOccupancies = new ArrayList<>();

        for (String bedType : bedTypes) {
            for (LocalDate date : dates) {
                FacilityBedOccupancy occupancy = FacilityBedOccupancy.builder()
                        .provider(provider)
                        .bedType(bedType)
                        .occupancyDate(date)
                        .occupancy(BedOccupancyConstants.ZERO)
                        .build();

                newOccupancies.add(occupancy);
            }
        }
        return newOccupancies;
    }


    public int saveBedOccupancy(List<BedOccupancyDto> bedOccupancies) {
        if (bedOccupancies == null || bedOccupancies.isEmpty()) {
            log.warn("Empty or null bed occupancy list received.");
            return BedOccupancyConstants.ZERO;
        }

        // Step 1: Filter and group incoming data
        Map<Long, FacilityBedCapacity> incomingMap = bedOccupancies.stream()
                .filter(dto -> dto != null && dto.providerId() != null)
                .collect(Collectors.toMap(BedOccupancyDto::providerId,
                        dto -> FacilityBedCapacity.builder()
                                .providerId(dto.providerId())
                                .totalBeds(dto.totalBeds())
                                .normalBeds(dto.normalBeds())
                                .icuBeds(dto.icuBeds())
                                .hduBeds(dto.hduBeds())
                                .dialysisBeds(dto.dialysisBeds())
                                .numberOfCots(dto.numberOfCots()).build()));

        if (incomingMap.isEmpty()) return BedOccupancyConstants.ZERO;

        // Step 2: Get latest records for each providerId
        List<Long> providerIds = new ArrayList<>(incomingMap.keySet());
        List<FacilityBedCapacity> latestRecords = capacityRepository.findLatestByProviderIds(providerIds);

        Map<Long, FacilityBedCapacity> latestMap = latestRecords.stream()
                .collect(Collectors.toMap(FacilityBedCapacity::getProviderId, Function.identity()));

        // Step 3: Compare and decide what to insert
        List<FacilityBedCapacity> toInsert = new ArrayList<>();

        for (Map.Entry<Long, FacilityBedCapacity> entry : incomingMap.entrySet()) {
            Long providerId = entry.getKey();
            FacilityBedCapacity incoming = entry.getValue();

            FacilityBedCapacity existing = latestMap.get(providerId);

            if (existing == null) {
                toInsert.add(incoming); // insert only if new or changed
            } else if (hasChangedBedOccupancy(existing, incoming)) {  // update existing record instead of inserting a new one
                existing.setTotalBeds(incoming.getTotalBeds());
                existing.setNormalBeds(incoming.getNormalBeds());
                existing.setIcuBeds(incoming.getIcuBeds());
                existing.setHduBeds(incoming.getHduBeds());
                existing.setDialysisBeds(incoming.getDialysisBeds());
                existing.setNumberOfCots(incoming.getNumberOfCots());
                toInsert.add(existing);
            }
        }

        // Step 4: Save new/changed records only
        if (!toInsert.isEmpty()) {
            capacityRepository.saveAll(toInsert);
            log.info("Inserted {} new FacilityBedCapacity records due to changes", toInsert.size());
        }

        return toInsert.size();
    }


    private boolean hasChangedBedOccupancy(FacilityBedCapacity existing, FacilityBedCapacity incoming) {
        return !Objects.equals(existing.getTotalBeds(), incoming.getTotalBeds())
                || !Objects.equals(existing.getNormalBeds(), incoming.getNormalBeds())
                || !Objects.equals(existing.getIcuBeds(), incoming.getIcuBeds())
                || !Objects.equals(existing.getHduBeds(), incoming.getHduBeds())
                || !Objects.equals(existing.getDialysisBeds(), incoming.getDialysisBeds())
                || !Objects.equals(existing.getNumberOfCots(), incoming.getNumberOfCots());
    }

    private void populateOccupancyFromCapacity(FacilityBedOccupancy occupancy, FacilityBedCapacity capacity, String claimId) {
        String bedType = Optional.ofNullable(occupancy.getBedType()).map(String::toLowerCase).orElse(StringUtils.EMPTY);

        int occupancyValue = switch (bedType) {
            case BedOccupancyConstants.NORMAL -> capacity.getNormalBeds();
            case BedOccupancyConstants.ICU -> capacity.getIcuBeds();
            case BedOccupancyConstants.HDU -> capacity.getHduBeds();
            case BedOccupancyConstants.DIALYSIS -> capacity.getDialysisBeds();
            case BedOccupancyConstants.COTS -> capacity.getNumberOfCots();
            case BedOccupancyConstants.TOTAL -> capacity.getTotalBeds();
            default -> {
                log.warn("Unknown bedType '{}' for providerId: {}", bedType, occupancy.getProvider().getId());
                yield BedOccupancyConstants.ZERO;
            }
        };
        int occupancyCount = occupancy.getOccupancy() + BedOccupancyConstants.ONE;
        occupancy.setOccupancy(occupancyCount);
        occupancy.setCreationDate(LocalDateTime.now());
        occupancy.setFacilityOccupancy(occupancyValue);
        Integer facilityOccupancy = occupancy.getFacilityOccupancy();
        if (facilityOccupancy != null && occupancyCount > facilityOccupancy) {
            occupancy.setFlagged(true);
            addClaimIfAbsent(occupancy, claimId);
        }
    }
}
