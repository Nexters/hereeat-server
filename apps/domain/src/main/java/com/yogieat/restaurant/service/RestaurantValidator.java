package com.yogieat.restaurant.service;

import com.yogieat.common.Region;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.domain.SuggestionRestaurant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 맛집 수집 과정에서 중복 검사를 수행하는 검증기
 * 비즈니스 로직에서 검증 로직을 분리
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RestaurantValidator {

    private static final String DUPLICATE_EXTERNAL_ID_REASON = "Restaurant already exists with externalId: %s";
    private static final String DUPLICATE_NAME_ADDRESS_REASON = "Restaurant already exists with same name and address";
    private static final String REQUIRED_NAME_REASON = "Restaurant name is required";
    private static final String REQUIRED_ADDRESS_REASON = "Restaurant address is required";

    private final RestaurantRepository restaurantRepository;

    /**
     * 주어진 장소에 대한 기존 맛집 데이터를 로드하여 배치 검증을 준비
     * DB 쿼리 횟수를 장소당 N번에서 1번으로 감소
     *
     * @param region 맛집 데이터를 로드할 장소
     */
    public ValidationContext prepareForBatchValidation(Region region) {
        log.info("Preparing batch validation cache for place: {}", region.getName());

        List<Restaurant> existingRestaurants = restaurantRepository.findByRegion(region);

        Set<String> cachedExternalIds = new HashSet<>();
        Set<String> cachedNameAddressPairs = new HashSet<>();

        for (Restaurant restaurant : existingRestaurants) {
            if (hasText(restaurant.externalId())) {
                cachedExternalIds.add(restaurant.externalId());
            }
            if (restaurant.name() != null && restaurant.address() != null) {
                cachedNameAddressPairs.add(createNameAddressKey(restaurant.name(), restaurant.address()));
            }
        }

        log.info("Loaded {} restaurants into cache ({} externalIds, {} name-address pairs)",
            existingRestaurants.size(), cachedExternalIds.size(), cachedNameAddressPairs.size());

        return new ValidationContext(cachedExternalIds, cachedNameAddressPairs);
    }

    /**
     * 캐시된 데이터를 사용하여 검증 수행 (배치 검증용)
     * prepareForBatchValidation을 먼저 호출해야 함
     *
     * @param suggestion Gemini로부터 받은 맛집 제안
     * @param externalId 카카오 장소 ID (카카오 장소를 찾지 못한 경우 null 가능)
     * @return 검증 상태와 사유를 포함한 ValidationResult
     */
    public ValidationResult duplicateValidateWithCache(
            ValidationContext context,
            SuggestionRestaurant suggestion,
            String externalId
    ) {
        return validateDuplicate(
                suggestion,
                externalId,
                new DuplicateLookupStrategy() {
                    @Override
                    public boolean isDuplicateExternalId(String id) {
                        return context.containsExternalId(id);
                    }

                    @Override
                    public boolean isDuplicateNameAddress(String name, String address) {
                        return context.containsNameAddress(name, address);
                    }
                },
                "cached"
        );
    }

    /**
     * 동일 배치 내에서 중복을 방지하기 위해 새로 저장된 맛집을 캐시에 추가
     *
     * @param externalId 카카오 장소 ID
     * @param name 맛집 이름
     * @param address 맛집 주소
     */
    public void addToCache(ValidationContext context, String externalId, String name, String address) {
        context.add(externalId, name, address);
    }

    /**
     * 캐싱을 위해 이름과 주소로 고유 키 생성
     */
    private String createNameAddressKey(String name, String address) {
        return name + "|" + address;
    }

    /**
     * 저장 전 맛집 중복 여부 검증
     *
     * @param suggestion Gemini로부터 받은 맛집 제안
     * @param externalId 카카오 장소 ID (카카오 장소를 찾지 못한 경우 null 가능)
     * @return 검증 상태와 사유를 포함한 ValidationResult
     */
    public ValidationResult duplicateValidate(SuggestionRestaurant suggestion, String externalId) {
        return validateDuplicate(
                suggestion,
                externalId,
                new DuplicateLookupStrategy() {
                    @Override
                    public boolean isDuplicateExternalId(String id) {
                        return isDuplicateByExternalId(id);
                    }

                    @Override
                    public boolean isDuplicateNameAddress(String name, String address) {
                        return isDuplicateByNameAndAddress(name, address);
                    }
                },
                "db"
        );
    }

    /**
     * externalId(카카오 장소 ID)로 맛집 존재 여부 확인
     *
     * @param externalId 카카오 장소 ID
     * @return 해당 externalId를 가진 맛집이 이미 존재하면 true
     */
    public boolean isDuplicateByExternalId(String externalId) {
        if (!hasText(externalId)) {
            return false;
        }
        return restaurantRepository.existsByExternalId(externalId);
    }

    /**
     * 이름과 주소 조합으로 맛집 존재 여부 확인
     * externalId를 사용할 수 없을 때 대체 수단으로 사용
     *
     * @param name 맛집 이름
     * @param address 맛집 주소
     * @return 동일한 이름과 주소를 가진 맛집이 이미 존재하면 true
     */
    public boolean isDuplicateByNameAndAddress(String name, String address) {
        if (!hasText(name) || !hasText(address)) {
            return false;
        }
        return restaurantRepository.existsByNameAndAddress(name, address);
    }

    private ValidationResult validateDuplicate(
            SuggestionRestaurant suggestion,
            String externalId,
            DuplicateLookupStrategy duplicateLookupStrategy,
            String source
    ) {
        if (hasText(externalId) && duplicateLookupStrategy.isDuplicateExternalId(externalId)) {
            log.debug("Duplicate restaurant detected by externalId ({}): {} ({})",
                    source, suggestion.name(), externalId);
            return ValidationResult.duplicate(DUPLICATE_EXTERNAL_ID_REASON.formatted(externalId));
        }

        if (duplicateLookupStrategy.isDuplicateNameAddress(suggestion.name(), suggestion.address())) {
            log.debug("Duplicate restaurant detected by name and address ({}): {} at {}",
                    source, suggestion.name(), suggestion.address());
            return ValidationResult.duplicate(DUPLICATE_NAME_ADDRESS_REASON);
        }

        if (!hasText(suggestion.name())) {
            return ValidationResult.invalid(REQUIRED_NAME_REASON);
        }

        if (!hasText(suggestion.address())) {
            return ValidationResult.invalid(REQUIRED_ADDRESS_REASON);
        }

        return ValidationResult.valid();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private interface DuplicateLookupStrategy {
        boolean isDuplicateExternalId(String externalId);
        boolean isDuplicateNameAddress(String name, String address);
    }

    public static final class ValidationContext {
        private final Set<String> externalIds;
        private final Set<String> nameAddressPairs;

        public ValidationContext(Set<String> externalIds, Set<String> nameAddressPairs) {
            this.externalIds = externalIds;
            this.nameAddressPairs = nameAddressPairs;
        }

        public boolean containsExternalId(String externalId) {
            return hasText(externalId) && externalIds.contains(externalId);
        }

        public boolean containsNameAddress(String name, String address) {
            return name != null && address != null && nameAddressPairs.contains(createNameAddressKey(name, address));
        }

        public void add(String externalId, String name, String address) {
            if (hasText(externalId)) {
                externalIds.add(externalId);
            }
            if (name != null && address != null) {
                nameAddressPairs.add(createNameAddressKey(name, address));
            }
        }

        private static String createNameAddressKey(String name, String address) {
            return name + "|" + address;
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }

    /**
     * 검증 결과
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
