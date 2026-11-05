package io.github.michalmszczepanski.project20_sor.geo;

import java.util.List;

/** Strategia szacowania dojazdu z jednego punktu do wielu celów naraz (jedno zapytanie zamiast wielu). */
@FunctionalInterface
public interface TravelEstimator {

	/** @return szacunki w tej samej kolejności co {@code destinations} */
	List<TravelEstimate> estimate(GeoPoint origin, List<GeoPoint> destinations);

}
