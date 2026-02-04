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

    private final RestaurantRepository restaurantRepository;

    // 배치 검증 캐시
    private final Set<String> cachedExternalIds = new HashSet<>();
    private final Set<String> cachedNameAddressPairs = new HashSet<>();

    /**
     * 주어진 장소에 대한 기존 맛집 데이터를 로드하여 배치 검증을 준비
     * DB 쿼리 횟수를 장소당 N번에서 1번으로 감소
     *
     * @param region 맛집 데이터를 로드할 장소
     */
    public void prepareForBatchValidation(Region region) {
        log.info("Preparing batch validation cache for place: {}", region.getName());

        List<Restaurant> existingRestaurants = restaurantRepository.findByRegion(region);

        cachedExternalIds.clear();
        cachedNameAddressPairs.clear();

        for (Restaurant restaurant : existingRestaurants) {
            if (restaurant.externalId() != null && !restaurant.externalId().isBlank()) {
                cachedExternalIds.add(restaurant.externalId());
            }
            if (restaurant.name() != null && restaurant.address() != null) {
                cachedNameAddressPairs.add(createNameAddressKey(restaurant.name(), restaurant.address()));
            }
        }

        log.info("Loaded {} restaurants into cache ({} externalIds, {} name-address pairs)",
            existingRestaurants.size(), cachedExternalIds.size(), cachedNameAddressPairs.size());
    }

    /**
     * 캐시된 데이터를 사용하여 검증 수행 (배치 검증용)
     * prepareForBatchValidation을 먼저 호출해야 함
     *
     * @param suggestion Gemini로부터 받은 맛집 제안
     * @param externalId 카카오 장소 ID (카카오 장소를 찾지 못한 경우 null 가능)
     * @return 검증 상태와 사유를 포함한 ValidationResult
     */
    public ValidationResult duplicateValidateWithCache(SuggestionRestaurant suggestion, String externalId) {
        // 1. externalId가 있으면 중복 검사 (카카오 장소를 찾은 경우)
        if (externalId != null && !externalId.isBlank()) {
            if (cachedExternalIds.contains(externalId)) {
                log.debug("Duplicate restaurant detected by externalId (cached): {} ({})",
                    suggestion.name(), externalId);
                return ValidationResult.duplicate(
                    "Restaurant already exists with externalId: " + externalId
                );
            }
        }

        // 2. 이름과 주소로 중복 검사 (Gemini 전용 맛집 대비)
        String nameAddressKey = createNameAddressKey(suggestion.name(), suggestion.address());
        if (cachedNameAddressPairs.contains(nameAddressKey)) {
            log.debug("Duplicate restaurant detected by name and address (cached): {} at {}",
                suggestion.name(), suggestion.address());
            return ValidationResult.duplicate(
                "Restaurant already exists with same name and address"
            );
        }

        // 3. 필수 필드 검증
        if (suggestion.name() == null || suggestion.name().isBlank()) {
            return ValidationResult.invalid("Restaurant name is required");
        }

        if (suggestion.address() == null || suggestion.address().isBlank()) {
            return ValidationResult.invalid("Restaurant address is required");
        }

        // 모든 검증 통과
        return ValidationResult.valid();
    }

    /**
     * 동일 배치 내에서 중복을 방지하기 위해 새로 저장된 맛집을 캐시에 추가
     *
     * @param externalId 카카오 장소 ID
     * @param name 맛집 이름
     * @param address 맛집 주소
     */
    public void addToCache(String externalId, String name, String address) {
        if (externalId != null && !externalId.isBlank()) {
            cachedExternalIds.add(externalId);
        }
        if (name != null && address != null) {
            cachedNameAddressPairs.add(createNameAddressKey(name, address));
        }
    }

    /**
     * 검증 캐시를 초기화
     * 다른 장소로 전환할 때 호출해야 함
     */
    public void clearCache() {
        cachedExternalIds.clear();
        cachedNameAddressPairs.clear();
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
        // 1. externalId가 있으면 중복 검사 (카카오 장소를 찾은 경우)
        if (externalId != null && !externalId.isBlank()) {
            if (isDuplicateByExternalId(externalId)) {
                log.debug("Duplicate restaurant detected by externalId: {} ({})",
                    suggestion.name(), externalId);
                return ValidationResult.duplicate(
                    "Restaurant already exists with externalId: " + externalId
                );
            }
        }

        // 2. 이름과 주소로 중복 검사 (Gemini 전용 맛집 대비)
        if (isDuplicateByNameAndAddress(suggestion.name(), suggestion.address())) {
            log.debug("Duplicate restaurant detected by name and address: {} at {}",
                suggestion.name(), suggestion.address());
            return ValidationResult.duplicate(
                "Restaurant already exists with same name and address"
            );
        }

        // 3. 필수 필드 검증
        if (suggestion.name() == null || suggestion.name().isBlank()) {
            return ValidationResult.invalid("Restaurant name is required");
        }

        if (suggestion.address() == null || suggestion.address().isBlank()) {
            return ValidationResult.invalid("Restaurant address is required");
        }

        // 모든 검증 통과
        return ValidationResult.valid();
    }

    /**
     * externalId(카카오 장소 ID)로 맛집 존재 여부 확인
     *
     * @param externalId 카카오 장소 ID
     * @return 해당 externalId를 가진 맛집이 이미 존재하면 true
     */
    public boolean isDuplicateByExternalId(String externalId) {
        if (externalId == null || externalId.isBlank()) {
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
        if (name == null || name.isBlank() || address == null || address.isBlank()) {
            return false;
        }
        return restaurantRepository.existsByNameAndAddress(name, address);
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
