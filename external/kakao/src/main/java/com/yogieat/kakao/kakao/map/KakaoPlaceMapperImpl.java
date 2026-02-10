package com.yogieat.kakao.kakao.map;

import com.yogieat.common.GeoJson;
import com.yogieat.external.kakao.KakaoPlaceMapper;
import com.yogieat.external.kakao.result.KaKaoPlaceDocumentResult;
import com.yogieat.external.kakao.result.KakaoRestaurantData;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class KakaoPlaceMapperImpl implements KakaoPlaceMapper {

    @Override
    public KakaoRestaurantData toDomainData(KaKaoPlaceDocumentResult document) {
        return new KakaoRestaurantData(
                document.id(),
                document.placeName(),
                document.roadAddressName() != null && !document.roadAddressName().isBlank()
                        ? document.roadAddressName()
                        : document.addressName(),
                document.placeUrl(),
                new GeoJson.Point(List.of(
                        Double.parseDouble(document.x()),
                        Double.parseDouble(document.y())
                ))
        );
    }
}
