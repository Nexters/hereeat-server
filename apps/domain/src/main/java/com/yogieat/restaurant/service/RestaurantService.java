package com.yogieat.restaurant.service;

import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.result.RestaurantAdminListItemResult;
import com.yogieat.restaurant.result.RestaurantAdminResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RestaurantService {
    private final RestaurantRepository restaurantRepository;

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

    @Transactional
    public Restaurant updateBy(Long id, RestaurantCommand.Patch command) {
        return restaurantRepository.applyAdminPatch(id, command);
    }

    @Transactional(readOnly = true)
    public long countAdminRestaurantList(RestaurantAdminListCriteria criteria) {
        return restaurantRepository.countAdminRestaurantList(criteria);
    }
}
