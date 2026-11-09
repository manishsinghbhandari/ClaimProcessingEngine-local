package io.coredge.health.claims.db.repositories.reportrepository;

import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.exception.app.DataNotFoundException;
import io.coredge.health.claims.exception.app.DatabaseException;
import io.coredge.health.claims.models.reportdashboard.ClaimReportExcelResponse;
import io.coredge.health.claims.models.reportdashboard.ClaimReportPdfResponse;
import io.coredge.health.claims.models.reportdashboard.ClaimReportUiResponse;
import io.coredge.health.claims.models.reportdashboard.ReportDashboardFilterOptions;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Repository
@RequiredArgsConstructor
@Slf4j
public class InsuranceClaimReportRepositoryImpl implements InsuranceClaimReportRepository{
    private final EntityManager entityManager;

    @Override
    public Page<ClaimReportUiResponse> findClaimsForUiReportPage(ReportDashboardFilterOptions filterOptions, Pageable pageable) {

        ReportDashboardFilterOptions filter = sanitizeWithDefaults(filterOptions);
        StringBuilder sql = new StringBuilder("""
            SELECT ic.claim_id, p.name, p.fid_code, p.level,
                   ic.claimed_total, COALESCE(ci.approved_sum, 0) AS approved_total,
                   ic.status, ic.created_date, ic.use
            FROM claims ic
            JOIN provider p ON p.id = ic.provider
            LEFT JOIN (
                SELECT
                     claim_id,
                     SUM(COALESCE(approved_price, 0)) AS approved_sum
                FROM claim_items
                GROUP BY claim_id
            ) ci ON ci.claim_id = ic.claim_id
            WHERE 1=1
        """);

        Map<String, Object> params = new HashMap<>();

        try {
            if (filter.startDate() != null) {
                sql.append(" AND ic.created_date >= :startDate");
                params.put("startDate", Timestamp.valueOf(filter.startDate().toLocalDate().atStartOfDay()));
            }
            if (filter.endDate() != null) {
                sql.append(" AND ic.created_date <= :endDate");
                params.put("endDate", Timestamp.valueOf(filter.endDate().toLocalDate().atTime(23, 59, 59)));
            }
            if (filter.fid_code() != null && !filter.fid_code().isEmpty()) {
                sql.append(" AND p.fid_code IN :fidCodes");
                params.put("fidCodes", filter.fid_code());
            }
            if (filter.facilityNames() != null && !filter.facilityNames().isEmpty()) {
                sql.append(" AND LOWER(p.name) IN :facilityNames");
                params.put("facilityNames", filter.facilityNames().stream().map(String::toLowerCase).toList());
            }
            if (filter.counties() != null && !filter.counties().isEmpty()) {
                sql.append(" AND p.county IN :counties");
                params.put("counties", filter.counties());
            }
            if (filter.levels() != null && !filter.levels().isEmpty()) {
                sql.append(" AND LOWER(BTRIM(p.level)) IN :levels");
                params.put(
                        "levels",
                        filter.levels().stream()
                                .filter(Objects::nonNull)
                                .map(s -> s.trim().toLowerCase())
                                .toList()
                );
            }
            if (filter.statusCode() != null && !filter.statusCode().isEmpty()) {
                sql.append(" AND ic.status IN :statuses");
                params.put("statuses", filter.statusCode());
            }
            if (filter.claimTypes() != null && !filter.claimTypes().isEmpty()) {
                sql.append(" AND ic.use IN :claimTypes");
                params.put("claimTypes", filter.claimTypes());
            }
            if (filter.sources() != null && !filter.sources().isEmpty()) {
                sql.append(" AND ic.source IN :sources");
                params.put("sources", filter.sources());
            }

            if (pageable.getSort().isSorted()) {
                Sort.Order order = pageable.getSort().iterator().next();
                String sortColumn = switch (order.getProperty()) {
                    case "createdDate" -> "ic.created_date";
                    default -> "ic.created_date";
                };
                String direction = order.getDirection().isAscending() ? "ASC" : "DESC";
                sql.append(" ORDER BY ").append(sortColumn).append(" ").append(direction);
            } else{
                sql.append(" ORDER BY ic.created_date DESC");
            }
            String countQueryStr = "SELECT COUNT(*) FROM (" + sql + ") AS count_alias";

            sql.append(" OFFSET :offset LIMIT :limit");
            params.put("offset", pageable.getOffset());
            params.put("limit", pageable.getPageSize());

            Query nativeQuery = entityManager.createNativeQuery(sql.toString());
            Query countQuery = entityManager.createNativeQuery(countQueryStr);

            params.forEach((key, value) -> {
                nativeQuery.setParameter(key, value);
                if (!key.equals("offset") && !key.equals("limit")) {
                    countQuery.setParameter(key, value);
                }
            });

            List<Object[]> rows = nativeQuery.getResultList();
            if (rows.isEmpty()) {
                throw new DataNotFoundException("No claims found for the given filters.");
            }
            List<ClaimReportUiResponse> responses = new ArrayList<>();

            for (Object[] row : rows) {
                if (row.length < 9) {
                    log.warn("Skipping malformed row: {}", Arrays.toString(row));
                    continue;
                }
                try {
                    UUID claimId = row[0] instanceof UUID ? (UUID) row[0] : null;
                    String name = (String) row[1];
                    String fidCode = (String) row[2];
                    String level = (String) row[3];
                    BigDecimal claimed = row[4] instanceof BigDecimal ? (BigDecimal) row[4] : BigDecimal.ZERO;
                    BigDecimal approved = row[5] instanceof BigDecimal ? (BigDecimal) row[5] : BigDecimal.ZERO;

                    ExtensionStateCodeEnum status;
                    try {
                        status = ExtensionStateCodeEnum.valueOf((String) row[6]);
                    } catch (Exception e) {
                        log.warn("Invalid status enum: {}", row[6]);
                        status = null;
                    }

                    LocalDateTime createdDate = row[7] instanceof Timestamp ? ((Timestamp) row[7]).toLocalDateTime() : null;
                    String claimType = (String) row[8];

                    responses.add(new ClaimReportUiResponse(
                            claimId, name, fidCode, level, claimed, approved, status, createdDate, claimType
                    ));
                } catch (Exception ex) {
                    log.error("Failed to map row: {}", Arrays.toString(row), ex);
                }
            }

            Long count = ((Number) countQuery.getSingleResult()).longValue();
            return new PageImpl<>(responses, pageable, count);
        } catch (PersistenceException | IllegalArgumentException e) {
            log.error("Database error while fetching claim report", e);
            throw new DatabaseException("Failed to fetch data from the database",e);
        }
    }

    @Override
    public List<ClaimReportExcelResponse> findClaimsForExcelReport(ReportDashboardFilterOptions filterOptions) {
        ReportDashboardFilterOptions filter = sanitizeWithDefaults(filterOptions);

        StringBuilder sql = new StringBuilder("""
            SELECT ic.claim_id, p.name, p.fid_code, m.name, m.cr_id, m.national_id, p.level,
                   ic.claimed_total, COALESCE(ci.approved_sum, 0) AS approved_total, ic.status, ic.created_date, ic.use,
                   p.county, p.sub_county, ic.source
            FROM claims ic
            JOIN provider p ON p.id = ic.provider
            JOIN patient m ON m.id = ic.patient
            LEFT JOIN (
                    SELECT
                        claim_id,
                        SUM(COALESCE(approved_price, 0)) AS approved_sum
                    FROM claim_items
                    GROUP BY claim_id
            ) ci ON ci.claim_id = ic.claim_id
            WHERE 1=1
        """);

        var params = new java.util.HashMap<String, Object>();

        if (filter.startDate() != null) {
            sql.append(" AND ic.created_date >= :startDate");
            params.put("startDate", Timestamp.valueOf(filter.startDate().toLocalDate().atStartOfDay()));
        }
        if (filter.endDate() != null) {
            sql.append(" AND ic.created_date <= :endDate");
            params.put("endDate", Timestamp.valueOf(filter.endDate().toLocalDate().atTime(23, 59, 59)));
        }
        if (filter.fid_code() != null && !filter.fid_code().isEmpty()) {
            sql.append(" AND p.fid_code IN :fidCodes");
            params.put("fidCodes", filter.fid_code());
        }
        if (filter.facilityNames() != null && !filter.facilityNames().isEmpty()) {
            sql.append(" AND LOWER(p.name) IN :facilityNames");
            params.put("facilityNames", filter.facilityNames().stream().map(String::toLowerCase).toList());
        }
        if (filter.counties() != null && !filter.counties().isEmpty()) {
            sql.append(" AND p.county IN :counties");
            params.put("counties", filter.counties());
        }
        if (filter.levels() != null && !filter.levels().isEmpty()) {
            sql.append(" AND LOWER(BTRIM(p.level)) IN :levels");
            params.put(
                    "levels",
                    filter.levels().stream()
                            .filter(Objects::nonNull)
                            .map(s -> s.trim().toLowerCase())
                            .toList()
            );
        }
        if (filter.statusCode() != null && !filter.statusCode().isEmpty()) {
            sql.append(" AND ic.status IN :statuses");
            params.put("statuses", filter.statusCode());
        }
        if (filter.claimTypes() != null && !filter.claimTypes().isEmpty()) {
            sql.append(" AND ic.use IN :claimTypes");
            params.put("claimTypes", filter.claimTypes());
        }
        if (filter.sources() != null && !filter.sources().isEmpty()) {
            sql.append(" AND ic.source IN :sources");
            params.put("sources", filter.sources());
        }

        Query query = entityManager.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);

        List<Object[]> results = query.getResultList();
        List<ClaimReportExcelResponse> mapped = new ArrayList<>();

        for (Object[] row : results) {
            if (row.length < 15) {
                log.warn("Skipping malformed row: {}", java.util.Arrays.toString(row));
                continue;
            }
            mapped.add(ClaimReportExcelResponse.builder()
                    .claimId(row[0] != null ? row[0].toString() : null)
                    .facilityName(row[1] != null ? row[1].toString() : "")
                    .facilityFrCode(row[2] != null ? row[2].toString() : "")
                    .patientName(row[3] != null ? row[3].toString() : "")
                    .patientCrId(row[4] != null ? row[4].toString() : "")
                    .patientNationalId(row[5] != null ? row[5].toString() : "")
                    .facilityLevel(row[6] != null ? row[6].toString() : "")
                    .claimedAmount((row[7] instanceof BigDecimal) ? (BigDecimal) row[7] : BigDecimal.ZERO)
                    .approvedAmount((row[8] instanceof BigDecimal) ? (BigDecimal) row[8] : BigDecimal.ZERO)
                    .status(row[9] != null ? row[9].toString() : "")
                    .claimDate(row[10] != null ? ((Timestamp) row[10]).toLocalDateTime() : null)
                    .claimType(row[11] != null ? row[11].toString() : "")
                    .county(row[12] != null ? row[12].toString() : "")
                    .subCounty(row[13] != null ? row[13].toString() : "")
                    .source(row[14] != null ? row[14].toString() : "")
                    .build());
        }
        return mapped;
    }

    @Override
    public List<ClaimReportPdfResponse> findClaimsForPdfReport(ReportDashboardFilterOptions filterOptions) {
        ReportDashboardFilterOptions filter = sanitizeWithDefaults(filterOptions);

        StringBuilder sql = new StringBuilder("""
        SELECT ic.claim_id, p.name, ic.claimed_total, COALESCE(ci.approved_sum, 0) AS approved_total, ic.status, ic.created_date,
               ic.use, p.county, p.sub_county, ic.source
        FROM claims ic
        JOIN provider p ON p.id = ic.provider
        LEFT JOIN (
                        SELECT
                            claim_id,
                            SUM(COALESCE(approved_price, 0)) AS approved_sum
                        FROM claim_items
                        GROUP BY claim_id
                    ) ci ON ci.claim_id = ic.claim_id
        WHERE 1=1
    """);

        var params = new java.util.HashMap<String, Object>();

        if (filter.startDate() != null) {
            sql.append(" AND ic.created_date >= :startDate");
            params.put("startDate", Timestamp.valueOf(filter.startDate().toLocalDate().atStartOfDay()));
        }
        if (filter.endDate() != null) {
            sql.append(" AND ic.created_date <= :endDate");
            params.put("endDate", Timestamp.valueOf(filter.endDate().toLocalDate().atTime(23, 59, 59)));
        }
        if (filter.fid_code() != null && !filter.fid_code().isEmpty()) {
            sql.append(" AND p.fid_code IN :fidCodes");
            params.put("fidCodes", filter.fid_code());
        }
        if (filter.facilityNames() != null && !filter.facilityNames().isEmpty()) {
            sql.append(" AND LOWER(p.name) IN :facilityNames");
            params.put("facilityNames", filter.facilityNames().stream().map(String::toLowerCase).toList());
        }
        if (filter.counties() != null && !filter.counties().isEmpty()) {
            sql.append(" AND p.county IN :counties");
            params.put("counties", filter.counties());
        }
        if (filter.levels() != null && !filter.levels().isEmpty()) {
            sql.append(" AND LOWER(BTRIM(p.level)) IN :levels");
            params.put(
                    "levels",
                    filter.levels().stream()
                            .filter(Objects::nonNull)
                            .map(s -> s.trim().toLowerCase())
                            .toList()
            );
        }
        if (filter.statusCode() != null && !filter.statusCode().isEmpty()) {
            sql.append(" AND ic.status IN :statuses");
            params.put("statuses", filter.statusCode());
        }
        if (filter.claimTypes() != null && !filter.claimTypes().isEmpty()) {
            sql.append(" AND ic.use IN :claimTypes");
            params.put("claimTypes", filter.claimTypes());
        }
        if (filter.sources() != null && !filter.sources().isEmpty()) {
            sql.append(" AND ic.source IN :sources");
            params.put("sources", filter.sources());
        }

        Query query = entityManager.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);

        List<Object[]> results = query.getResultList();
        List<ClaimReportPdfResponse> mapped = new ArrayList<>();

        for (Object[] row : results) {
            if (row.length < 10) {
                //Safeguard against malformed row
                log.warn("Skipping malformed row: {}", java.util.Arrays.toString(row));
                continue;
            }
            mapped.add(ClaimReportPdfResponse.builder()
                    .claimId(row[0] != null ? row[0].toString() : "")
                    .facilityName(row[1] != null ? (String) row[1] : "")
                    .claimedAmount((row[2] instanceof BigDecimal) ? (BigDecimal) row[2] : BigDecimal.ZERO)
                    .approvedAmount((row[3] instanceof BigDecimal) ? (BigDecimal) row[3] : BigDecimal.ZERO)
                    .status(row[4] != null ? (String) row[4] : "")
                    .claimDate(row[5] != null ? ((Timestamp) row[5]).toLocalDateTime() : null)
                    .claimType(row[6] != null ? (String) row[6] : "")
                    .county(row[7] != null ? (String) row[7] : "")
                    .subCounty(row[8] != null ? (String) row[8] : "")
                    .source(row[9] != null ? (String) row[9] : "")
                    .build());
        }
        if (mapped.isEmpty()) {
            log.info("No PDF claim report data found for filter: {}", filter);
        }
        return mapped;
    }


    private ReportDashboardFilterOptions sanitizeWithDefaults(ReportDashboardFilterOptions filterOptions) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(23, 59, 59);

        LocalDateTime startDate = Optional.ofNullable(filterOptions.startDate()).orElse(todayStart);
        LocalDateTime endDate = Optional.ofNullable(filterOptions.endDate()).orElse(todayEnd);

        //end date should not be after today
        if (endDate.isAfter(todayEnd)) {
            endDate = todayEnd;
        }

        if (startDate.toLocalDate().equals(endDate.toLocalDate())) {
            endDate = endDate.toLocalDate().atTime(23, 59, 59);
        }

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must not be after end date.");
        }

        return new ReportDashboardFilterOptions(
                filterOptions.fid_code(),
                filterOptions.statusCode(),
                filterOptions.levels(),
                filterOptions.accessPoints(),
                filterOptions.counties(),
                filterOptions.facilityNames(),
                filterOptions.claimTypes(),
                filterOptions.sources(),
                startDate,
                endDate
        );
    }
}
