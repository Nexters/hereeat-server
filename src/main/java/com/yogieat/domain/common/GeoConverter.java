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

    public Point geoJsonPointToJtsPoint(GeoJson.Point point) {
        List<Double> coords = point.getCoordinates();
        Coordinate coordinate = new Coordinate(coords.getFirst(), coords.get(1));
        return GEOMETRY_FACTORY.createPoint(coordinate);
    }
}
