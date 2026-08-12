package com.yogieat.restaurant.service;

import com.yogieat.restaurant.domain.CreateRestaurant;
import com.yogieat.restaurant.domain.Restaurant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RestaurantCommandService {

    private final RestaurantRepository restaurantRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Restaurant save(CreateRestaurant createRestaurant) {
        return restaurantRepository.save(createRestaurant);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Restaurant saveOrRevive(CreateRestaurant createRestaurant) {
        return restaurantRepository.saveOrRevive(createRestaurant);
    }
}
