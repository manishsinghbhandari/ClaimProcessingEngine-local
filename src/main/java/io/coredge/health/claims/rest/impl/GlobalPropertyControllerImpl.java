package io.coredge.health.claims.rest.impl;

import io.coredge.health.claims.rest.GlobalPropertyController;
import io.coredge.health.claims.service.GlobalPropertyService;
import io.coredge.health.claims.service.properties.GlobalPropertyRequest;
import io.coredge.health.claims.service.properties.GlobalPropertyResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static io.coredge.health.claims.constants.Constants.AdminEndPoints.GLOBAL_PROPERTY;

@RestController
@RequestMapping(GLOBAL_PROPERTY)
public class GlobalPropertyControllerImpl implements GlobalPropertyController {

    private final GlobalPropertyService globalPropertyService;

    @Autowired
    public GlobalPropertyControllerImpl(GlobalPropertyService globalPropertyService) {
        this.globalPropertyService = globalPropertyService;
    }

    @Override
    public ResponseEntity<GlobalPropertyResponse> createGlobalProperty(GlobalPropertyRequest request) {
        return ResponseEntity.ok(globalPropertyService.createGlobalProperty(request));
    }

    @Override
    public ResponseEntity<CollectionModel<EntityModel<GlobalPropertyResponse>>> getGlobalProperties(String search, int page, int size, String sortBy, String direction) {
        return ResponseEntity.ok(globalPropertyService.fetchGlobalProperties(search , PageRequest.of(page, size, Sort.by(Sort.Direction.valueOf(direction) , sortBy))));
    }

    @Override
    public ResponseEntity<GlobalPropertyResponse> getGlobalProperty(Long id) {
        return ResponseEntity.ok(globalPropertyService.fetchGlobalProperty(id));
    }

    @Override
    public ResponseEntity<GlobalPropertyResponse> updateGlobalProperty(Long id, GlobalPropertyRequest request) {
        return ResponseEntity.ok(globalPropertyService.updateGlobalProperty(id , request));
    }

    @Override
    public ResponseEntity<String> updateGlobalProperty(Long id ,boolean status) {
        globalPropertyService.updateGlobalPropertyStatus(id,status);
        return ResponseEntity.ok("Status Updated Successfully");
    }

    @Override
    public ResponseEntity<String> deleteGlobalProperty(Long id) {
        globalPropertyService.deleteGlobalProperty(id);
        return ResponseEntity.ok("Status Deleted Successfully");
    }

    @Override
    public ResponseEntity<Map<String, String>> getResolverProperty() {
        return ResponseEntity.ok(globalPropertyService.getProperties());
    }

    @Override
    public ResponseEntity<String> getResolverProperty(String key) {
        return ResponseEntity.ok(globalPropertyService.getPropertyValue(key));
    }

}
