package com.yogieat.common;

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
     * GeoJson.Point를 JTS Point로 변환
     */
    public Point geoJsonPointToJtsPoint(GeoJson.Point point) {
        List<Double> coords = point.getCoordinates();
        Coordinate coordinate = new Coordinate(coords.getFirst(), coords.get(1));
        return GEOMETRY_FACTORY.createPoint(coordinate);
    }

    /**
     * 문자열 좌표(경도, 위도)로부터 JTS Point 생성
     * 외부 API 좌표 변환에 주로 사용 (예: 카카오맵)
     *
     * @param longitude X 좌표 (경도)
     * @param latitude Y 좌표 (위도)
     * @return SRID 4326 (WGS84)을 사용하는 JTS Point
     */
    public Point createPointFromCoordinates(String longitude, String latitude) {
        double lon = Double.parseDouble(longitude);
        double lat = Double.parseDouble(latitude);
        return GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat));
    }

    /**
     * double 좌표(경도, 위도)로부터 JTS Point 생성
     *
     * @param longitude X 좌표 (경도)
     * @param latitude Y 좌표 (위도)
     * @return SRID 4326 (WGS84)을 사용하는 JTS Point
     */
    public Point createPointFromCoordinates(double longitude, double latitude) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }
}
