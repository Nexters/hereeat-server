package com.yogieat.domain.common;

import java.util.List;
import lombok.Getter;

@Getter
public enum Region {
    HONGDAE("홍대입구역", new GeoJson.Point(List.of(126.92378, 37.55684))),
    GANGNAM("강남역", new GeoJson.Point(List.of(127.0276, 37.4979))),
    GONGDEOK("공덕역", new GeoJson.Point(List.of(126.95070, 37.54437)))
    ;


    private final String name;
    private final GeoJson.Point coordinatesStandard;

    Region(String name, GeoJson.Point coordinatesStandard) {
        this.name = name;
        this.coordinatesStandard = coordinatesStandard;
    }
}
