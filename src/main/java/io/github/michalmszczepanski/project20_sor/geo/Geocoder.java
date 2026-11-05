package io.github.michalmszczepanski.project20_sor.geo;

import java.util.List;

/** Zamienia wpisany adres na miejsca na mapie. */
@FunctionalInterface
public interface Geocoder {

	/**
	 * @return znalezione miejsca, najlepiej pasujące najpierw; pusta lista, gdy nic nie znaleziono
	 * @throws GeocodingException gdy usługa nie odpowiada
	 */
	List<Place> search(String query);

	record Place(String label, GeoPoint location) {
	}

}
