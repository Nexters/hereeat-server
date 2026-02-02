package com.yogieat.external.kakao.result;

import org.locationtech.jts.geom.Point;

/**
 * Domain DTO for restaurant data from Kakao Place API
 * Decouples external API layer from JPA entities (Clean Architecture)
 */
public record KakaoRestaurantData(
    String externalId,
    String name,
    String address,
    String mapUrl,
    Point location
) {}
