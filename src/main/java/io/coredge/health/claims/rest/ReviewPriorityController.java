package io.coredge.health.claims.rest;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.enums.ReviewPriorityEnum;
import io.coredge.health.claims.models.DropDown;
import io.coredge.health.claims.models.ReviewPriorityDTO;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


public interface ReviewPriorityController {

    @GetMapping(path = Constants.AdminEndPoints.PRIORITY_GET_CATEGORY, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<DropDown>> fetchCategories();

    @GetMapping(path = Constants.AdminEndPoints.PRIORITY_GET_SUBCATEGORY, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<DropDown>> fetchSubCategories(@PathVariable() ReviewPriorityEnum category ,
                                                      @RequestParam @Size(min = 3, message = "Search Value must be atleast 3 characters")
                                                      String searchValue);

    @GetMapping("/")
    ResponseEntity<List<ReviewPriorityDTO>> getPriorities();

    @PostMapping("/")
    ResponseEntity<String> updatePriorities(@RequestBody List<ReviewPriorityDTO> priorities);
}
