package com.yogieat.domain.restaurant.service;

import com.yogieat.external.ai.gemini.RestaurantSuggestion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Validator for checking duplicate restaurants during collection process
 * Separates validation concerns from business logic
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RestaurantValidator {

    private final RestaurantRepository restaurantRepository;

    /**
     * Validate if restaurant is duplicate before saving
     *
     * @param suggestion Restaurant suggestion from Gemini
     * @param externalId Kakao place ID (can be null if Kakao place not found)
     * @return ValidationResult containing validation status and reason
     */
    public ValidationResult duplicateValidate(RestaurantSuggestion suggestion, String externalId) {
        // 1. Check duplicate by externalId if available (Kakao place was found)
        if (externalId != null && !externalId.isBlank()) {
            if (isDuplicateByExternalId(externalId)) {
                log.debug("Duplicate restaurant detected by externalId: {} ({})",
                    suggestion.name(), externalId);
                return ValidationResult.duplicate(
                    "Restaurant already exists with externalId: " + externalId
                );
            }
        }

        // 2. Check duplicate by name and address (fallback for Gemini-only restaurants)
        if (isDuplicateByNameAndAddress(suggestion.name(), suggestion.address())) {
            log.debug("Duplicate restaurant detected by name and address: {} at {}",
                suggestion.name(), suggestion.address());
            return ValidationResult.duplicate(
                "Restaurant already exists with same name and address"
            );
        }

        // 3. Validate required fields
        if (suggestion.name() == null || suggestion.name().isBlank()) {
            return ValidationResult.invalid("Restaurant name is required");
        }

        if (suggestion.address() == null || suggestion.address().isBlank()) {
            return ValidationResult.invalid("Restaurant address is required");
        }

        // All validations passed
        return ValidationResult.valid();
    }

    /**
     * Check if restaurant exists by externalId (Kakao place ID)
     *
     * @param externalId Kakao place ID
     * @return true if restaurant with this externalId already exists
     */
    public boolean isDuplicateByExternalId(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return false;
        }
        return restaurantRepository.existsByExternalId(externalId);
    }

    /**
     * Check if restaurant exists by name and address combination
     * Used as fallback when externalId is not available
     *
     * @param name Restaurant name
     * @param address Restaurant address
     * @return true if restaurant with same name and address already exists
     */
    public boolean isDuplicateByNameAndAddress(String name, String address) {
        if (name == null || name.isBlank() || address == null || address.isBlank()) {
            return false;
        }
        return restaurantRepository.existsByNameAndAddress(name, address);
    }

    /**
     * Result of validation
     */
    public record ValidationResult(
        boolean isValid,
        boolean isDuplicate,
        String reason
    ) {
        public static ValidationResult valid() {
            return new ValidationResult(true, false, null);
        }

        public static ValidationResult duplicate(String reason) {
            return new ValidationResult(false, true, reason);
        }

        public static ValidationResult invalid(String reason) {
            return new ValidationResult(false, false, reason);
        }
    }
}
