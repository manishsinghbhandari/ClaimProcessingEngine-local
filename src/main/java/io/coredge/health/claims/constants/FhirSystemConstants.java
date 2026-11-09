package io.coredge.health.claims.constants;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FhirSystemConstants {

    @Value("${HIE_ORG_ID_SYSTEM:https://fr-kenyahie.health/facility-codes}")
    public String HIE_ORG_ID_SYSTEM;

    @Value("${HIE_HEALTH_WORKER_ID_SYSTEM:http://hwr-kenyahie.health}")
    public String HIE_HEALTH_WORKER_ID_SYSTEM;

    @Value("${HIE_MEMBER_ID_SYSTEM:http://cr-kenyahie.health/concepts}")
    public String HIE_MEMBER_ID_SYSTEM;

    public static final List<String> VALID_CLAIM_SUB_TYPES = List.of("ip", "op", "emr");
    public FhirSystemConstants(){

    }
}
