package com.yogieat.domain.restaurant.service;

import com.yogieat.domain.restaurant.domain.Restaurant;
import com.yogieat.global.error.CustomException;
import com.yogieat.global.error.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RestaurantService {
    private final RestaurantRepository restaurantRepository;

    @Transactional(readOnly = true)
    public Restaurant findById(Long id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.RESTAURANT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<Restaurant> findByIds(List<Long> ids) {
        return restaurantRepository.findByIds(ids);
    }
}
