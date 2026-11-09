package io.coredge.health.claims;

import io.camunda.zeebe.spring.client.annotation.Deployment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableRetry
@ComponentScan(basePackages = "io.coredge.health.claims")
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
@EnableCaching
@Deployment(resources = {"civil_claim_process.bpmn", "civil_sha_preauth_process.bpmn", "ShaClaimApprovalDecisionV2.dmn", "ShaPreAuthApprovalDecisionV2.dmn"})
public class ClaimsProcessApplication {
  public static void main(String[] args) {
    SpringApplication.run(ClaimsProcessApplication.class, args);
  }
}