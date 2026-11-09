package io.coredge.health.claims.rest.impl;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.helpers.Helper;
import io.coredge.health.claims.models.phccalculation.*;
import io.coredge.health.claims.service.CountyPopulationService;
import io.coredge.health.claims.service.PHCClaimAsyncService;
import io.coredge.health.claims.service.PHCClaimService;
import jakarta.ws.rs.DefaultValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.apache.jena.sparql.engine.optimizer.reorder.ReorderTransformationSubstitution.log;


@RestController
@RequestMapping(Constants.EndPoints.PHC_CLAIM_API_PATH)
public class PHCClaimControllerImpl {

    @Autowired
    private PHCClaimService phcClaimService;

    @Autowired
    private PHCClaimAsyncService phcClaimAsyncService;

    @GetMapping(path = Constants.EndPoints.PHC_CLAIM_API_GET_COUNTY_WISE_PROVIDER)
    public ResponseEntity<List<PHCClaimsCountyWiseProviderMonthDTO>> getCountyWiseProvider(@PathVariable("month") String monthName) {
        LocalDateTime[] dateRange = Helper.getStartAndEndDateTime(Helper.decodeValue(monthName));

        Optional<List<PHCClaimsCountyWiseProviderMonthDTO>> response = phcClaimService.getProvidersGroupedByCounty(dateRange[0], dateRange[1]);
        return response.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping(path = Constants.EndPoints.PHC_CLAIM_API_GET_COUNTY_WITH_PROVIDER_COUNT)
    public ResponseEntity<List<PHCClaimsCountyProviderCountDTO>> getCountyProviders(@PathVariable("month") String monthName) {
        Optional<List<PHCClaimsCountyProviderCountDTO>> response = phcClaimAsyncService.findProvidersGroupByCounty(monthName);
        return response.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping(path = Constants.EndPoints.PHC_CLAIM_API_GET_PROVIDER_WITH_CLAIMS_COUNT)
    public ResponseEntity<List<PHCClaimsProviderClaimCountDTO>> getProvidersClaims(@PathVariable("county") String countyName, @PathVariable("month") String monthName) {
        LocalDateTime[] dateRange = Helper.getStartAndEndDateTime(Helper.decodeValue(monthName));
        Optional<List<PHCClaimsProviderClaimCountDTO>> response = phcClaimService.getProviderWithClaimsCount(dateRange[0], dateRange[1], Helper.decodeValue(countyName), Helper.decodeValue(monthName));
        return response.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping(path = Constants.EndPoints.PHC_CLAIM_API_GET_CLAIMS_SUMMARY)
    public ResponseEntity<List<PHCClaimsSummaryDTO>> getClaimsSummary(@PathVariable("provider") Long providerId, @PathVariable("county") String countyName, @PathVariable("month") String monthName, @PathVariable("saveFlag") @DefaultValue("N") String saveYorN) {
        Boolean saveFlag = !saveYorN.equalsIgnoreCase("N");
        List<PHCClaimsSummaryDTO> summaryList = phcClaimService.findClaimsSummary(Helper.decodeValue(monthName), Helper.decodeValue(countyName), providerId, saveFlag);
        return ResponseEntity.ok(summaryList);
    }


    @PostMapping(path = Constants.EndPoints.PHC_CLAIM_EXPORT_SUMMARY)
    public ResponseEntity<Resource> exportFile(@PathVariable("provider") Long providerId, @PathVariable("county") String countyName, @PathVariable("month") String monthName,
                                               @RequestParam String fileType) {

        Map<String, Object> fileData = phcClaimService.generateFile(providerId, Helper.decodeValue(countyName), Helper.decodeValue(monthName), Helper.decodeValue(fileType), false);

        ByteArrayInputStream stream = (ByteArrayInputStream) fileData.get("stream");
        String fileName = (String) fileData.get("fileName");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(stream));
    }

//    @GetMapping(path = Constants.EndPoints.PHC_CLAIM_API_GET_COUNTRY_WISE_SUMMARY)
//    public ResponseEntity<List<PHCClaimsSummaryCountry>> exportFileCountryWise(@PathVariable("country") String countryName, @PathVariable("month") String monthName) {
//        return ResponseEntity.ok(phcClaimService.findCountryWiseData(Helper.decodeValue(countryName), Helper.decodeValue(monthName)));
//    }

    @PostMapping(path = Constants.EndPoints.PHC_CLAIM_COUNTRY_WISE_EXPORT_SUMMARY)
    public ResponseEntity<Resource> exportFileCountryWise(
            @PathVariable("country") String countryName,
            @PathVariable("month") String monthName,
            @RequestParam("fileType") String fileType) {
        long startTime = System.currentTimeMillis();
        Map<String, Object> fileData = phcClaimService.generateFileCountryWiseData(
                Helper.decodeValue(countryName),
                Helper.decodeValue(monthName),
                Helper.decodeValue(fileType)
        );

        ByteArrayInputStream csvStream = (ByteArrayInputStream) fileData.get("stream");
        String fileName = (String) fileData.get("fileName");
        String zipFileName = fileName.replace(".csv", ".zip");

        try (ByteArrayOutputStream zipOutputStream = new ByteArrayOutputStream();
             ZipOutputStream zipStream = new ZipOutputStream(zipOutputStream)) {
            zipStream.putNextEntry(new ZipEntry(fileName));
            try (BufferedInputStream csvBufferedStream = new BufferedInputStream(csvStream)) {
                csvBufferedStream.transferTo(zipStream);
            }
            zipStream.closeEntry();
            zipStream.finish();

            long endTime = System.currentTimeMillis();
            log.info("Overall Time Taken: {} ms", endTime - startTime);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + zipFileName)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new ByteArrayResource(zipOutputStream.toByteArray()));
        }  catch (IOException e) {
            long endTime = System.currentTimeMillis();
            log.info("Overall Time Taken:: {} ms", endTime - startTime);
            log.error("Error generating ZIP file: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    @GetMapping(path = Constants.EndPoints.PHC_CLAIM_API_CALCULATION_ALL)
    public ResponseEntity<?> phcCalculationAll(@PathVariable("month") String monthName) {
        phcClaimService.automatePhcClaimsCalculation(Helper.decodeValue(monthName));
        return ResponseEntity.accepted().build();
    }

    @GetMapping(path = Constants.EndPoints.PHC_CLAIM_API_SEND_FOR_PAYMENTS_ALL)
    public ResponseEntity<?> phcCalPayments(@PathVariable("month") String monthName) {
        phcClaimService.automatePhcClaimsPaymentProcessing(Helper.decodeValue(monthName));
        return ResponseEntity.accepted().build();
    }

}
