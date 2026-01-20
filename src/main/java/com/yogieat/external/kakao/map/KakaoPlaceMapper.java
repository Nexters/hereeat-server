package com.yogieat.external.kakao.map;

import com.yogieat.domain.common.GeoConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Mapper for Kakao Place API responses
 * Follows Clean Architecture: External layer returns domain DTOs, not entities
 */
@Component
@RequiredArgsConstructor
public class KakaoPlaceMapper {

    private final GeoConverter geoConverter;

    /**
     * Convert Kakao Place API document to domain DTO
     * Does NOT depend on JPA entities (Clean Architecture principle)
     *
     * @param document Kakao Place API response
     * @return Domain DTO with restaurant data
     */
    public KakaoRestaurantData toDomainData(KaKaoPlaceDocument document) {
        return new KakaoRestaurantData(
            document.id(),
            document.placeName(),
            document.roadAddressName() != null && !document.roadAddressName().isBlank()
                ? document.roadAddressName()
                : document.addressName(),
            document.placeUrl(),
            geoConverter.createPointFromCoordinates(document.x(), document.y())
        );
    }
}
