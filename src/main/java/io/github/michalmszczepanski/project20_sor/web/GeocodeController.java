package io.github.michalmszczepanski.project20_sor.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.michalmszczepanski.project20_sor.geo.Geocoder;

@RestController
@RequestMapping("/api/geocode")
class GeocodeController {

	static final int MIN_QUERY = 3;

	static final int MAX_QUERY = 200;

	private final Geocoder geocoder;

	GeocodeController(Geocoder geocoder) {
		this.geocoder = geocoder;
	}

	@GetMapping
	List<PlaceView> search(@RequestParam String q) {
		String query = q.trim();
		if (query.length() < MIN_QUERY || query.length() > MAX_QUERY) {
			throw new IllegalArgumentException("Adres musi mieć od " + MIN_QUERY + " do " + MAX_QUERY + " znaków");
		}
		return geocoder.search(query).stream().map(PlaceView::of).toList();
	}

	record PlaceView(String label, double lat, double lng) {

		static PlaceView of(Geocoder.Place place) {
			return new PlaceView(place.label(), place.location().lat(), place.location().lng());
		}

	}

}
