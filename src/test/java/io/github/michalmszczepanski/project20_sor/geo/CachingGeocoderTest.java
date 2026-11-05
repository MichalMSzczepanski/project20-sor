package io.github.michalmszczepanski.project20_sor.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class CachingGeocoderTest {

	private final List<String> calls = new ArrayList<>();

	private boolean failing;

	private final Geocoder delegate = query -> {
		calls.add(query);
		if (failing) {
			throw new GeocodingException("HTTP 503", null);
		}
		return List.of(new Geocoder.Place(query, new GeoPoint(52, 21)));
	};

	private final CachingGeocoder geocoder = new CachingGeocoder(delegate, 2);

	@Test
	void sameQueryIsAskedOnceIgnoringCaseAndSpaces() {
		geocoder.search("Pisz");
		geocoder.search("  pisz ");
		geocoder.search("PISZ");

		assertThat(calls).containsExactly("Pisz");
	}

	@Test
	void forgetsLeastRecentlyUsed() {
		geocoder.search("a b c");
		geocoder.search("d e f");
		geocoder.search("a b c");
		geocoder.search("g h i");

		geocoder.search("d e f");

		assertThat(calls).containsExactly("a b c", "d e f", "g h i", "d e f");
	}

	@Test
	void errorsAreNotCached() {
		failing = true;
		assertThatThrownBy(() -> geocoder.search("Pisz")).isInstanceOf(GeocodingException.class);
		failing = false;

		assertThat(geocoder.search("Pisz")).hasSize(1);
		assertThat(calls).hasSize(2);
	}

}
