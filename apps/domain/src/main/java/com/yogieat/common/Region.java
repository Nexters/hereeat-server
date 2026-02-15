package com.yogieat.common;

import java.util.List;
import lombok.Getter;

@Getter
public enum Region {
    HONGDAE("홍대입구역", new GeoJson.Point(List.of(126.92378, 37.55684))),
    GANGNAM("강남역", new GeoJson.Point(List.of(127.0276, 37.4979))),
    GONGDEOK("공덕역", new GeoJson.Point(List.of(126.95070, 37.54437))),
    EULJIRO3GA("을지로3가역", new GeoJson.Point(List.of(126.99224, 37.56623))),
    SADANG("사당역", new GeoJson.Point(List.of(126.98231, 37.47625))),
    JONGNO3GA("종로3가역", new GeoJson.Point(List.of(126.99171, 37.57270))),
    JAMSIL("잠실역", new GeoJson.Point(List.of(127.10128, 37.51379))),
    SAMGAKJI("삼각지역", new GeoJson.Point(List.of(126.97346, 37.53453))),
    ;


    private final String name;
    private final GeoJson.Point coordinatesStandard;

    Region(String name, GeoJson.Point coordinatesStandard) {
        this.name = name;
        this.coordinatesStandard = coordinatesStandard;
    }
}
