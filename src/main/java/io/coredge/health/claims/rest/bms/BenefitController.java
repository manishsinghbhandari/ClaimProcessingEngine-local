package io.coredge.health.claims.rest.bms;

import io.coredge.health.claims.db.entity.BMS.Benefit;
import io.coredge.health.claims.service.BMS.BenefitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static io.coredge.health.claims.constants.Constants.EndPoints.*;

@RestController
@CrossOrigin(origins = "*", maxAge = 3600)
@RequestMapping(BMS_BENIFIT)
@RequiredArgsConstructor
public class BenefitController {

    private final BenefitService benefitService;

    @GetMapping
    public List<Benefit> getAllBenefits() {
        return benefitService.getAllBenefits();
    }

    @GetMapping(BMS_BENIFIT_ID)
    public ResponseEntity<Benefit> getBenefitById(@PathVariable Long id) {
        return ResponseEntity.ok(benefitService.getBenefitById(id));
    }

    @GetMapping(BMS_BENIFIT_CODE)
    public ResponseEntity<Benefit> getBenefitByCode(@PathVariable String code) {
        return ResponseEntity.ok(benefitService.getBenefitByCode(code));
    }

    @GetMapping(BMS_BENIFIT_NAME)
    public List<Benefit> getBenefitsByName(@PathVariable String name) {
        return benefitService.getBenefitsByName(name);
    }

}