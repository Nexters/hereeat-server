package com.yogieat.domain.common;

import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Component;

@Component
public class GeoConverter {
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    /**
     * Convert GeoJson.Point to JTS Point
     */
    public Point geoJsonPointToJtsPoint(GeoJson.Point point) {
        List<Double> coords = point.getCoordinates();
        Coordinate coordinate = new Coordinate(coords.getFirst(), coords.get(1));
        return GEOMETRY_FACTORY.createPoint(coordinate);
    }

    /**
     * Create JTS Point from string coordinates (longitude, latitude)
     * Commonly used for converting external API coordinates (e.g., Kakao Maps)
     *
     * @param longitude X coordinate (경도)
     * @param latitude Y coordinate (위도)
     * @return JTS Point with SRID 4326 (WGS84)
     */
    public Point createPointFromCoordinates(String longitude, String latitude) {
        double lon = Double.parseDouble(longitude);
        double lat = Double.parseDouble(latitude);
        return GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat));
    }

    /**
     * Create JTS Point from double coordinates (longitude, latitude)
     *
     * @param longitude X coordinate (경도)
     * @param latitude Y coordinate (위도)
     * @return JTS Point with SRID 4326 (WGS84)
     */
    public Point createPointFromCoordinates(double longitude, double latitude) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }
}
