package com.yogieat.domain.restaurant.service;

import com.yogieat.domain.restaurant.domain.Restaurant;
import com.yogieat.global.error.CustomException;
import com.yogieat.global.error.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RestaurantService {
    private final RestaurantRepository restaurantRepository;

    public Restaurant findById(Long id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.RESTAURANT_NOT_FOUND));
    }

    public List<Restaurant> findByIds(List<Long> ids) {
        return restaurantRepository.findByIds(ids);
    }
}
