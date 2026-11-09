package io.coredge.health.claims.rest;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.db.entity.AdminLevelOne;
import io.coredge.health.claims.db.entity.AdminLevelPopulation;
import io.coredge.health.claims.service.CountyPopulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping(Constants.EndPoints.COUNTY_POPULATION_BASE_URL)
@RequiredArgsConstructor
public class CountyPopulationController {

    private final CountyPopulationService countyPopulationService;

    @PostMapping(Constants.EndPoints.CREATE_OR_UPDATE_COUNTY_POPULATION)
    public ResponseEntity<String> saveOrUpdateCountyPopulation(@RequestBody AdminLevelPopulation countyPopulation) {
        // Extract county name from AdminLevelOne entity if available, or use a default value
        String countyName = countyPopulation.getAdminLevelEntity() != null ? 
                countyPopulation.getAdminLevelEntity().getName() : "Unknown County";
        
        countyPopulationService.saveOrUpdateCountyPopulation(
                countyName, 
                countyPopulation.getPopulation(), 
                countyPopulation.getMonth());
        return ResponseEntity.ok("Success");
    }

    @GetMapping(Constants.EndPoints.GET_COUNTY_POPULATION)
    public ResponseEntity<AdminLevelPopulation> getCountyPopulation(@RequestParam String month, @RequestParam String county) {
        Optional<AdminLevelPopulation> countyPopulation = countyPopulationService.getAdminLevelPopulation(month, county);
        return countyPopulation.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(Constants.EndPoints.GET_ALL_COUNTY_POPULATIONS)
    public ResponseEntity<List<AdminLevelPopulation>> getCountyPopulationsByMonth(@RequestParam String month) {
        List<AdminLevelPopulation> countyPopulations = countyPopulationService.getAdminLevelPopulationsByMonth(month);
        return ResponseEntity.ok(countyPopulations);
    }

    @DeleteMapping(Constants.EndPoints.DELETE_COUNTY_POPULATION)
    public ResponseEntity<Void> deleteCountyPopulation(@PathVariable String county, @PathVariable String monthName) {
        countyPopulationService.deleteCountyPopulation(county, monthName);
        return ResponseEntity.noContent().build();
    }
}

