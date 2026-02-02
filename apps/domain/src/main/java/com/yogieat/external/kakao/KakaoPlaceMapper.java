package com.yogieat.external.kakao;

import com.yogieat.common.GeoConverter;
import com.yogieat.external.kakao.result.KaKaoPlaceDocumentResult;
import com.yogieat.external.kakao.result.KakaoRestaurantData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 카카오 장소 API 응답 매퍼
 * 클린 아키텍처 준수: 외부 계층은 엔티티가 아닌 도메인 DTO 반환
 */
@Component
@RequiredArgsConstructor
public class KakaoPlaceMapper {

    private final GeoConverter geoConverter;

    /**
     * 카카오 장소 API 문서를 도메인 DTO로 변환
     * JPA 엔티티에 의존하지 않음 (클린 아키텍처 원칙)
     *
     * @param document 카카오 장소 API 응답
     * @return 맛집 데이터를 포함한 도메인 DTO
     */
    public KakaoRestaurantData toDomainData(KaKaoPlaceDocumentResult document) {
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
