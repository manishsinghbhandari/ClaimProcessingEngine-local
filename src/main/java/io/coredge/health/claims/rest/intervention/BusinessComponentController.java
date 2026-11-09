package io.coredge.health.claims.rest.intervention;

import static io.coredge.health.claims.constants.Constants.AdminEndPoints.INTERVENTION_BUSINESS_COMPONENT;

import io.coredge.health.claims.models.intervention.request.BusinessComponentRequest;
import io.coredge.health.claims.models.intervention.response.BusinessComponentResponse;
import io.coredge.health.claims.service.intervention.BusinessComponentService;
import io.coredge.health.claims.service.intervention.PageDropDown;
import jakarta.validation.Valid;

import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(INTERVENTION_BUSINESS_COMPONENT)
@Slf4j
@RequiredArgsConstructor
public class BusinessComponentController {

  private static final String BUSINESS_COMPONENT_FILE_NAME = "businessComponent";
  private static final String DATE_FORMAT = "yyyy-MM-dd_HH:mm:ss";

  private final BusinessComponentService businessComponentService;

  @PostMapping("/")
  public ResponseEntity<BusinessComponentResponse> createBusinessComponent(
      @Valid @RequestBody BusinessComponentRequest request) throws InvocationTargetException, IllegalAccessException {
    return ResponseEntity.ok(businessComponentService.addBusinessComponent(request));
  }

  @GetMapping("/code/{code}")
  public ResponseEntity<List<BusinessComponentResponse>> getBusinessComponent(
      @PathVariable("code") String code) {
    List<BusinessComponentResponse> response = businessComponentService.getBusinessComponent(code);
    if (response.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(response);
  }

  @GetMapping("/id/{id}")
  public ResponseEntity<BusinessComponentResponse> getBusinessComponent(
      @PathVariable("id") Long id) {
    return ResponseEntity.ok(businessComponentService.getBusinessComponent(id));
  }

  @GetMapping("/")
  public ResponseEntity<List<BusinessComponentResponse>> getBusinessComponent() {
    return ResponseEntity.ok(businessComponentService.getBusinessComponent());
  }

  @GetMapping("/pageDropDown")
  public ResponseEntity<PageDropDown> getPageDropDown() {
    return ResponseEntity.ok(businessComponentService.getPageDropDown());
  }

  @GetMapping("/dropDown")
  public ResponseEntity<Map<String, Long>> getBusinessComponentDropDown() {
    return ResponseEntity.ok(businessComponentService.getBusinessComponentDropDown());
  }

  @GetMapping("/filter")
  public ResponseEntity<List<String>> getBusinessComponentFilterDropDown() {
    return ResponseEntity.ok(businessComponentService.getBusinessComponentFilterDropDown());
  }

  @GetMapping("/dropdown/{code}")
  public ResponseEntity<Map<String, Long>> getBusinessComponentDropDown(
      @PathVariable("code") String code) {
    return ResponseEntity.ok(businessComponentService.getInterventionDropDown(code));
  }

  @PutMapping("/{id}")
  public ResponseEntity<BusinessComponentResponse> updateBusinessComponentByRule(
      @Valid @RequestBody BusinessComponentRequest rule, @PathVariable("id") Long id) throws InvocationTargetException, IllegalAccessException {
    return ResponseEntity.ok(businessComponentService.updateBusinessComponent(rule, id));
  }

  @PatchMapping("/{id}/{status}")
  public ResponseEntity<Void> updateBusinessComponentStatus(
      @PathVariable("id") Long id, @PathVariable("status") boolean status) throws InvocationTargetException, IllegalAccessException {
    businessComponentService.updateBusinessComponentRuleStatus(id, status);
    return ResponseEntity.ok().build();
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteMapping(@PathVariable("id") Long id) {
    businessComponentService.deleteBusinessComponentRule(id);
    return ResponseEntity.ok().build();
  }

  @PostMapping(path = "/upload", consumes = "multipart/form-data")
  public ResponseEntity<List<String>> uploadRules(@RequestParam("file") MultipartFile file) {
    return ResponseEntity.ok(businessComponentService.uploadComponents(file));
  }

  // demo
  @GetMapping("/download")
  public ResponseEntity<?> downloadBusinessComponentExcel() {
      byte[] excelData = businessComponentService.downloadFile();

      // Set headers for file download
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
      headers.setContentDispositionFormData("attachment", BUSINESS_COMPONENT_FILE_NAME + LocalDateTime.now().format(DateTimeFormatter.ofPattern(DATE_FORMAT)) +".xlsx");

      return ResponseEntity.ok().headers(headers).body(excelData);
  }
}
