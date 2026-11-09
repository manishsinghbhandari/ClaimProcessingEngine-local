package io.coredge.health.claims.rest;

import io.coredge.health.claims.service.properties.GlobalPropertyRequest;
import io.coredge.health.claims.service.properties.GlobalPropertyResponse;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

public interface GlobalPropertyController {

    @PostMapping("/")
    ResponseEntity<GlobalPropertyResponse> createGlobalProperty(@RequestBody GlobalPropertyRequest globalProperties);

    @GetMapping("/")
    ResponseEntity<CollectionModel<EntityModel<GlobalPropertyResponse>>> getGlobalProperties(@RequestParam(required = false) String search,
                                                                                             @RequestParam(defaultValue = "0") int page,
                                                                                             @RequestParam(defaultValue = "10") int size,
                                                                                             @RequestParam(defaultValue = "id") String sortBy,
                                                                                             @RequestParam(defaultValue = "ASC") String direction);

    @GetMapping("/{id}")
    ResponseEntity<GlobalPropertyResponse> getGlobalProperty(@PathVariable Long id );

    @PutMapping("/{id}")
    ResponseEntity<GlobalPropertyResponse> updateGlobalProperty(@PathVariable Long id, @RequestBody GlobalPropertyRequest globalProperties);

    @PatchMapping("/{id}/{status}")
    ResponseEntity<String> updateGlobalProperty(@PathVariable Long id ,@PathVariable boolean status);

    @DeleteMapping("/{id}")
    ResponseEntity<String> deleteGlobalProperty(@PathVariable Long id);

    @GetMapping("/resolver")
    ResponseEntity<Map<String, String>> getResolverProperty();

    @GetMapping("/resolver/{key}")
    ResponseEntity<String> getResolverProperty(@PathVariable String key);


}
