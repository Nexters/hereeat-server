package com.yogieat.restaurant.service;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.external.kakao.result.KakaoPlaceDetailFetchResult;
import com.yogieat.external.kakao.result.KakaoPlaceDetailFetchStatus;
import com.yogieat.restaurant.domain.CreateRestaurant;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.result.RestaurantAdminListItemResult;
import com.yogieat.restaurant.result.RestaurantAdminResult;
import com.yogieat.restaurant.result.RestaurantDetailResult;
import com.yogieat.util.LockManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RestaurantService {
    private static final String RESTAURANT_CREATE_LOCK_PREFIX = "restaurant:create:";

    private final RestaurantRepository restaurantRepository;
    private final RestaurantCommandService restaurantCommandService;
    private final RestaurantAdminLookupService restaurantAdminLookupService;
    private final RestaurantValidator restaurantValidator;
    private final LockManager lockManager;

    @Transactional(readOnly = true)
    public Restaurant getBy(Long id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.RESTAURANT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<Restaurant> findByIds(List<Long> ids) {
        return restaurantRepository.findByIds(ids);
    }

    @Transactional(readOnly = true)
    public List<RestaurantAdminListItemResult> findAdminRestaurants(RestaurantAdminListCriteria criteria, int page, int size) {
        return restaurantRepository.findPageRestaurants(criteria, page, size);
    }

    @Transactional(readOnly = true)
    public RestaurantAdminResult.Detail getAdminRestaurantDetailBy(Long id) {
        return restaurantRepository.findAdminRestaurantDetailById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.RESTAURANT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public RestaurantDetailResult getRestaurantDetailBy(Long id) {
        return restaurantRepository.findRestaurantDetailById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.RESTAURANT_NOT_FOUND));
    }

    @Transactional
    public Restaurant updateBy(Long id, RestaurantCommand.Patch command) {
        return restaurantRepository.applyAdminPatch(id, command);
    }

    @Transactional
    public RestaurantAdminResult.Create createRestaurant(RestaurantCommand.Create command) {
        restaurantValidator.validateCreateCommand(command);
        String externalId = command.externalId().strip();

        return lockManager.executeWithLock(
                buildCreateLockKey(externalId),
                () -> restaurantRepository.findByExternalId(externalId)
                        .map(restaurant -> RestaurantAdminResult.Create.duplicated(restaurant.id()))
                        .orElseGet(() -> createRestaurantByExternalId(command, externalId))
        );
    }

    @Transactional
    public void deleteBy(Long id) {
        restaurantRepository.deleteBy(id);
    }

    @Transactional(readOnly = true)
    public long countAdminRestaurantList(RestaurantAdminListCriteria criteria) {
        return restaurantRepository.countAdminRestaurantList(criteria);
    }

    @Transactional(readOnly = true)
    public long countActiveRestaurants() {
        return restaurantRepository.countActiveRestaurants();
    }

    private RestaurantAdminResult.Create createRestaurantByExternalId(
            RestaurantCommand.Create command,
            String externalId
    ) {
        KakaoPlaceDetailFetchResult detailResult = restaurantAdminLookupService.fetchPlaceDetail(externalId);
        if (detailResult.status() == KakaoPlaceDetailFetchStatus.NOT_FOUND) {
            throw new CustomException(ErrorCode.RESTAURANT_NOT_FOUND);
        }
        if (detailResult.status() != KakaoPlaceDetailFetchStatus.SUCCESS || detailResult.detail() == null) {
            throw new CustomException(ErrorCode.KAKAO_API_ERROR);
        }

        restaurantValidator.validateCreateDetail(detailResult.detail());

        try {
            Restaurant createdRestaurant = restaurantCommandService.save(
                    CreateRestaurant.fromKakaoPlaceDetail(
                            detailResult.detail(),
                            command.categoryId(),
                            externalId,
                            command.region(),
                            command.description()
                    )
            );
            return RestaurantAdminResult.Create.created(createdRestaurant.id());
        } catch (DataIntegrityViolationException e) {
            return restaurantRepository.findByExternalId(externalId)
                    .map(restaurant -> RestaurantAdminResult.Create.duplicated(restaurant.id()))
                    .orElseThrow(() -> new CustomException(ErrorCode.KAKAO_API_ERROR));
        }
    }

    private String buildCreateLockKey(String externalId) {
        return RESTAURANT_CREATE_LOCK_PREFIX + externalId;
    }
}
