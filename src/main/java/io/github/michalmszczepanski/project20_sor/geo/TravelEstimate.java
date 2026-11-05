package io.github.michalmszczepanski.project20_sor.geo;

/**
 * @param distanceKm odległość, którą przyjął estymator (po drogach albo w linii prostej — patrz {@code method})
 * @param minutes    szacowany czas dojazdu
 */
public record TravelEstimate(double distanceKm, int minutes, Method method) {

	public enum Method {
		ROAD, STRAIGHT_LINE
	}

}
