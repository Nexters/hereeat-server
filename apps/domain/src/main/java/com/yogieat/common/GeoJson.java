package com.yogieat.common;

import java.util.List;
import lombok.Getter;

public class GeoJson {
    public String getType() {
        return null;
    }

    @Getter
    public static class Point extends GeoJson {
        private final List<Double> coordinates;

        public Point(List<Double> coordinates) {
            this.coordinates = coordinates;
        }

        @Override
        public String getType() {
            return "Point";
        }
    }
}
