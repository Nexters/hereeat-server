package com.yogieat.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import lombok.Getter;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = GeoJson.Point.class, name = "Point")
})
public class GeoJson {
    @JsonIgnore
    public String getType() {
        return null;
    }

    @Getter
    public static class Point extends GeoJson {
        private final List<Double> coordinates;

        public Point(List<Double> coordinates) {
            this.coordinates = coordinates;
        }

        @JsonIgnore
        @Override
        public String getType() {
            return "Point";
        }
    }
}
