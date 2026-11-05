package io.github.michalmszczepanski.project20_sor.route;

import java.util.Optional;

import io.github.michalmszczepanski.project20_sor.geo.GeoPoint;

/** Strategia wyznaczania trasy dla jednego trybu podróży. */
public interface RoutePlanner {

	RouteMode mode();

	/**
	 * @return trasa albo pusta, gdy usługa nie znalazła połączenia
	 * @throws RoutingException gdy usługa nie odpowiada
	 */
	Optional<Route> plan(GeoPoint from, GeoPoint to);

}
