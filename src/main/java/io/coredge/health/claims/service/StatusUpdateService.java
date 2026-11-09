package io.coredge.health.claims.service;

import io.coredge.health.claims.service.cpp.CppService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StatusUpdateService {
    private final AfyanguService afyanguService;
    private final CppService cppService;

    @Value("${cpp.data.enable:false}")
    private Boolean cppDataEnable;


    public StatusUpdateService(AfyanguService afyanguService, CppService cppService) {
        this.afyanguService = afyanguService;
        this.cppService = cppService;
    }

    public void triggerCallBackStatusUpdate(String claimId, Boolean isAfyangu, Boolean isCpp , Boolean cppBackProcess) {
        log.info("StatusUpdateService, claimId:{}, isAfyangu: {}, isCpp: {}", claimId, isAfyangu, isCpp);
        if (isAfyangu) {
            try {
                afyanguService.sendWebhook(claimId);
            } catch (Exception e) {
                log.error("Error while sending afyangu webhook", e);
            }

        }
        if (isCpp && cppDataEnable) {
            try {
                cppService.sendWebhook(claimId, cppBackProcess);
            } catch (Exception e) {
                log.error("Error while sending Cpp webhook", e);
            }

        }
    }

}
