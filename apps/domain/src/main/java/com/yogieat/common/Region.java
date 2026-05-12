package com.yogieat.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import java.util.Objects;

public final class Region {

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
        return new Region(region, null, null);
    }

    @JsonValue
    public String code() {
        return code;
    }

    public String name() {
        return code;
    }

    public String getName() {
        return name != null ? name : code;
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
