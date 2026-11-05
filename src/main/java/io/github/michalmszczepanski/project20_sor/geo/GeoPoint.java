package io.github.michalmszczepanski.project20_sor.geo;

public record GeoPoint(double lat, double lng) {

	private static final double EARTH_RADIUS_KM = 6371.0088;

	public GeoPoint {
		if (!(lat >= -90 && lat <= 90) || !(lng >= -180 && lng <= 180)) {
			throw new IllegalArgumentException("Nieprawidłowe współrzędne: " + lat + ", " + lng);
		}
	}

	/** Odległość po wielkim kole (wzór haversine). */
	public double distanceKmTo(GeoPoint other) {
		double dLat = Math.toRadians(other.lat - lat);
		double dLng = Math.toRadians(other.lng - lng);
		double a = Math.pow(Math.sin(dLat / 2), 2)
				+ Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(other.lat)) * Math.pow(Math.sin(dLng / 2), 2);
		return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(a)));
	}

}
