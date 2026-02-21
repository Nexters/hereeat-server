package com.yogieat.common;

import java.util.List;

public final class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoUtils() {
    }

    public static boolean isValidPoint(GeoJson.Point point) {
        return point != null
                && point.getCoordinates() != null
                && point.getCoordinates().size() >= 2
                && point.getCoordinates().get(0) != null
                && point.getCoordinates().get(1) != null;
    }

    public static double calculateDistanceKm(GeoJson.Point from, GeoJson.Point to) {
        List<Double> fromCoordinates = from.getCoordinates();
        List<Double> toCoordinates = to.getCoordinates();

        double lat1 = fromCoordinates.get(1);
        double lon1 = fromCoordinates.get(0);
        double lat2 = toCoordinates.get(1);
        double lon2 = toCoordinates.get(0);

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
