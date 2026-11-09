package io.coredge.health.claims.rest.impl;

import io.coredge.health.claims.enums.ReviewPriorityEnum;
import io.coredge.health.claims.models.DropDown;
import io.coredge.health.claims.models.ReviewPriorityDTO;
import io.coredge.health.claims.rest.ReviewPriorityController;
import io.coredge.health.claims.service.ReviewPriorityService;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static io.coredge.health.claims.constants.Constants.AdminEndPoints.*;

@RestController
@RequestMapping(PRIORITY_REVIEW)
public class ReviewPriorityControllerImpl implements ReviewPriorityController {

    private final ReviewPriorityService reviewPriorityService;

    public ReviewPriorityControllerImpl(ReviewPriorityService reviewPriorityService) {
        this.reviewPriorityService = reviewPriorityService;
    }

    @Override
    public ResponseEntity<List<DropDown>> fetchCategories() {
         return ResponseEntity.ok(reviewPriorityService.getCategories());
    }

    @Override
    public ResponseEntity<List<DropDown>> fetchSubCategories(@PathVariable() ReviewPriorityEnum category ,
                                                             @RequestParam @Size(min = 3, message = "Search Value must be atleast 3 characters")
                                                             String searchValue) {
        return ResponseEntity.ok(reviewPriorityService.getSubCategories(category , searchValue));
    }

    @Override
    public ResponseEntity<List<ReviewPriorityDTO>> getPriorities() {
        return ResponseEntity.ok(reviewPriorityService.getPriorities());
    }

    @Override
    public ResponseEntity<String> updatePriorities(@RequestBody List<ReviewPriorityDTO> priorities) {
        reviewPriorityService.updatePriorities(priorities);
        return ResponseEntity.ok("Priority are updated");
    }

}
