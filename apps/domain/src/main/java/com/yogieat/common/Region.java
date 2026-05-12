package com.yogieat.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class Region {
    private static final Map<String, Region> DEFAULT_REGIONS = Map.ofEntries(
            Map.entry("HONGDAE", legacyRegion("HONGDAE", "홍대입구역", 126.92378, 37.55684)),
            Map.entry("GANGNAM", legacyRegion("GANGNAM", "강남역", 127.0276, 37.4979)),
            Map.entry("GONGDEOK", legacyRegion("GONGDEOK", "공덕역", 126.95070, 37.54437)),
            Map.entry("EULJIRO3GA", legacyRegion("EULJIRO3GA", "을지로3가역", 126.99224, 37.56623)),
            Map.entry("SADANG", legacyRegion("SADANG", "사당역", 126.98231, 37.47625)),
            Map.entry("JONGNO3GA", legacyRegion("JONGNO3GA", "종로3가역", 126.99171, 37.57270)),
            Map.entry("JAMSIL", legacyRegion("JAMSIL", "잠실역", 127.10128, 37.51379)),
            Map.entry("SAMGAKJI", legacyRegion("SAMGAKJI", "삼각지역", 126.97346, 37.53453))
    );

    private final String code;
    private final String name;
    private final GeoJson.Point coordinatesStandard;

    private Region(String code, String name, GeoJson.Point coordinatesStandard) {
        this.code = normalizeCode(code);
        this.name = normalizeName(name);
        this.coordinatesStandard = coordinatesStandard;
    }

    public static Region of(String code, String name, GeoJson.Point coordinatesStandard) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return new Region(code, name, coordinatesStandard);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Region fromString(String region) {
        if (region == null || region.isBlank()) {
            return null;
        }
        String code = normalizeCode(region);
        Region defaultRegion = DEFAULT_REGIONS.get(code);
        return defaultRegion != null ? defaultRegion : new Region(code, null, null);
    }

    @JsonValue
    public String code() {
        return code;
    }

    public String name() {
        return code;
    }

    public String getName() {
        return name;
    }

    public GeoJson.Point getCoordinatesStandard() {
        return coordinatesStandard;
    }

    private static String normalizeCode(String code) {
        return code.strip().toUpperCase(Locale.ROOT);
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.strip();
    }

    private static Region legacyRegion(String code, String name, double longitude, double latitude) {
        return new Region(code, name, new GeoJson.Point(List.of(longitude, latitude)));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Region region)) {
            return false;
        }
        return Objects.equals(code, region.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code);
    }

    @Override
    public String toString() {
        return code;
    }
}
