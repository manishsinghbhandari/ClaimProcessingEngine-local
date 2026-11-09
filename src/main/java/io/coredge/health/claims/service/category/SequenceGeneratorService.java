package io.coredge.health.claims.service.category;

import io.coredge.health.claims.db.entity.category.ReasonCategory;
import io.coredge.health.claims.db.repositories.category.ReasonCategoryRepository;
import io.coredge.health.claims.exception.category.ReasonCategoryNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SequenceGeneratorService {

    private final ReasonCategoryRepository rejectionCategoryRepository;

    /**
     * Get the next formatted sequence for a category
     */
    @Transactional
    public String getNextFormattedSequence(Long categoryId) {
        log.debug("Generating next sequence for category ID: {}", categoryId);

        // 1. Get category
        ReasonCategory category = rejectionCategoryRepository.findById(categoryId)
                .orElseThrow(ReasonCategoryNotFoundException::new);

        // 2. Get the current sequence (default to "000" if null/empty)
        String currentSequence = category.getLastSequence();
        if (currentSequence == null || currentSequence.trim().isEmpty()) {
            currentSequence = "000";
            log.debug("No existing sequence found, defaulting to: {}", currentSequence);
        }

        // 3. Parse to integer and increment
        int currentValue;
        try {
            currentValue = Integer.parseInt(currentSequence);
        } catch (NumberFormatException e) {
            log.error("Invalid sequence format: '{}' for category ID: {}", currentSequence, categoryId);
            currentValue = 0; // Reset to 0 if an invalid format
        }

        // 4. Increment and validate
        int nextValue = currentValue + 1;

        // 5. Format as 3-digit string
        String nextSequence = String.format("%03d", nextValue);

        // 6. Update the category with a new sequence
        category.setLastSequence(nextSequence);
        rejectionCategoryRepository.save(category);

        log.info("Generated sequence {} for category {} (ID: {})",
                nextSequence, category.getPrefix(), categoryId);

        return nextSequence;
    }
}