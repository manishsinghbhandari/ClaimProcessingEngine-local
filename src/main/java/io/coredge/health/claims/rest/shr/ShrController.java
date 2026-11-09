package io.coredge.health.claims.rest.shr;

import io.coredge.health.claims.service.shr.ShrService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static io.coredge.health.claims.constants.Constants.EndPoints.IPS_API_MAPPING;
import static io.coredge.health.claims.constants.Constants.EndPoints.IPS_API_PATH;

@RestController
@RequestMapping(IPS_API_PATH)
public class ShrController {

    private final ShrService shrService;

    public ShrController(ShrService shrService) {
        this.shrService = shrService;
    }

    @GetMapping(IPS_API_MAPPING)
    public String getFhirBundle(@PathVariable String bundleId) {
        return shrService.getIpsDetails(bundleId);
    }
}
