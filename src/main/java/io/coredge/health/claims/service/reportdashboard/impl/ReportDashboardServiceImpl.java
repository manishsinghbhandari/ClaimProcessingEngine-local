package io.coredge.health.claims.service.reportdashboard.impl;

import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.ProviderRepository;
import io.coredge.health.claims.db.repositories.reportrepository.InsuranceClaimReportRepository;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.exception.app.DataNotFoundException;
import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.exception.models.ErrorCode;
import io.coredge.health.claims.models.reportdashboard.ClaimReportExcelResponse;
import io.coredge.health.claims.models.reportdashboard.ClaimReportPdfResponse;
import io.coredge.health.claims.models.reportdashboard.ClaimReportUiResponse;
import io.coredge.health.claims.models.reportdashboard.ReportDashboardFilterOptions;
import io.coredge.health.claims.service.reportdashboard.ReportDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.stereotype.Service;

import jakarta.persistence.Query;
import jakarta.persistence.EntityManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportDashboardServiceImpl implements ReportDashboardService {

    @Autowired
    private final ProviderRepository providerRepository;
    @Autowired
    private final PagedResourcesAssembler<ClaimReportUiResponse> pagedResourcesAssembler;
    @Autowired
    private InsuranceClaimsRepository insuranceClaimsRepository;
    @Autowired
    private InsuranceClaimReportRepository insuranceClaimReportRepository;
    @Autowired
    private EntityManager entityManager;

    @Override
    public Page<String> searchAllFidCode(String searchValue) {
        Page<String> page = searchByFidCodes(searchValue);

        if (!page.hasContent()) {
            throw new DataNotFoundException();
        }
        return page;
    }

    private Page<String> searchByFidCodes(String searchKey) {
        int page = 0;
        int size = 10;
        String sortBy = "fidCode";
        return providerRepository.findDistinctByFidCodeContaining(
                searchKey,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, sortBy))
        );

    }

    @Cacheable(value = "levels")
    public List<String> searchAllLevels() {
        return providerRepository.findAllDistinctLevels();
    }

    @Cacheable(value = "countyList")
    public List<String> getAllCounty() {
        return providerRepository.findAllByCounty();
    }

    public List<String> getAllSubCounty() {
        return providerRepository.findAllDistinctSubcounty();
    }

    @Override
    public Page<ClaimReportUiResponse> getClaimsUiReportPage(
            ReportDashboardFilterOptions filterRequest, Pageable pageable
    ) {
        return insuranceClaimReportRepository.findClaimsForUiReportPage(
                filterRequest, pageable);
    }

    @Override
    public List<ClaimReportExcelResponse> getClaimsForExcel(ReportDashboardFilterOptions filterRequest) {
        return insuranceClaimReportRepository.findClaimsForExcelReport(filterRequest);
    }

    @Override
    public List<ClaimReportPdfResponse> getClaimsForPdf(ReportDashboardFilterOptions filterRequest) {
        return insuranceClaimReportRepository.findClaimsForPdfReport(filterRequest);
    }

    @Override
    public ByteArrayInputStream generateExcelReport(List<ClaimReportExcelResponse> data) {
        return ExcelGenerator.generate(data);
    }

    @Override
    public ByteArrayInputStream generatePdfReport(List<ClaimReportPdfResponse> data) {
        return PdfGenerator.generate(data);
    }

    @Override
    public List<String> getAllSources() {
        return insuranceClaimsRepository.findDistinctSources();
    }

    @Override
    public Map<String, Object> generateExcelFileStream(List<ClaimReportExcelResponse> data) {
        if (data.isEmpty()) {
            throw new DataNotFoundException("No data available for the provided filters.") ;
        }
        if (data.size() > 1_000_000) {
            throw new RuntimeException("Excel export failed: Cannot export more than 1 million records.");
        }

        try (ByteArrayOutputStream excelOut = new ByteArrayOutputStream()) {
            ExcelGenerator.writeToWorkbook(data, excelOut);
            byte[] excelBytes = excelOut.toByteArray();

            Map<String, Object> result = new HashMap<>();
            if (excelBytes.length > 2 * 1024 * 1024) { // If size > 2MB, zip it
                try (ByteArrayOutputStream zipOut = new ByteArrayOutputStream();
                     ZipOutputStream zos = new ZipOutputStream(zipOut)) {

                    zos.putNextEntry(new ZipEntry("claim_report.xlsx"));
                    zos.write(excelBytes);
                    zos.closeEntry();
                    zos.finish();

                    result.put("stream", new ByteArrayInputStream(zipOut.toByteArray()));
                    result.put("fileName", "claim_report.zip");
                }
            } else {
                result.put("stream", new ByteArrayInputStream(excelBytes));
                result.put("fileName", "claim_report.xlsx");
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Excel file generation failed.", e);
        }
    }

    @Override
    public List<String> getAllClaimTypes() {
        return insuranceClaimsRepository.findDistinctClaimTypes();
    }
}
