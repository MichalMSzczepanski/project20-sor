package io.github.michalmszczepanski.project20_sor.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NominatimGeocoderTest {

	private final RestClient.Builder builder = RestClient.builder().baseUrl("https://nominatim.test");

	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

	private final NominatimGeocoder geocoder = new NominatimGeocoder(builder.build(),
			new MinIntervalThrottle(Duration.ZERO, Clock.systemUTC(), duration -> {
			}));

	@Test
	void searchesPolandAndParsesRealResponse() {
		server.expect(requestTo(Matchers.startsWith("https://nominatim.test/search")))
				.andExpect(queryParam("q", "Pisz"))
				.andExpect(queryParam("countrycodes", "pl"))
				.andExpect(queryParam("format", "jsonv2"))
				.andExpect(queryParam("limit", "5"))
				.andExpect(queryParam("addressdetails", "1"))
				.andRespond(withSuccess(new ClassPathResource("fixtures/nominatim-pisz.json"), MediaType.APPLICATION_JSON));

		List<Geocoder.Place> places = geocoder.search("Pisz");

		server.verify();
		assertThat(places).extracting(Geocoder.Place::label).containsExactly(
				"Pisz, powiat piski, warmińsko-mazurskie",
				"Pisz, Tumiany, powiat olsztyński, warmińsko-mazurskie",
				"powiat piski, warmińsko-mazurskie");
		assertThat(places.get(0).location()).isEqualTo(new GeoPoint(53.6290631, 21.8082539));
	}

	@Test
	void buildsStreetLabelsInCitiesAndDropsDuplicates() {
		server.expect(requestTo(Matchers.startsWith("https://nominatim.test/search")))
				.andRespond(withSuccess(new ClassPathResource("fixtures/nominatim-bursztynowa.json"),
						MediaType.APPLICATION_JSON));

		List<Geocoder.Place> places = geocoder.search("Bursztynowa 2, Warszawa");

		// Prawdziwa odpowiedź ma 5 wyników, w tym dwa razy ten sam adres budynku w Wawrze.
		assertThat(places).extracting(Geocoder.Place::label).containsExactly(
				"Międzyleski Szpital Specjalistyczny, Bursztynowa 2, Wawer, Warszawa",
				"Bursztynowa 2, Wawer, Warszawa",
				"Bursztynowa 2, Wesoła, Warszawa",
				"Kaplica pw. Świętego Ojca Pio, Bursztynowa 2, Wawer, Warszawa");
	}

	@Test
	void fallsBackToDisplayNameWithoutAddressDetails() {
		assertThat(NominatimGeocoder.label(new NominatimGeocoder.Result("52", "21", "Coś, Gdzieś, Polska", null, null)))
				.isEqualTo("Coś, Gdzieś");
	}

	@Test
	void encodesSpacesAndCommasInQuery() {
		server.expect(requestTo(Matchers.startsWith("https://nominatim.test/search")))
				.andExpect(queryParam("q", Matchers.anyOf(Matchers.is("Bursztynowa%202,%20Warszawa"),
						Matchers.is("Bursztynowa+2,+Warszawa"), Matchers.is("Bursztynowa 2, Warszawa"))))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		assertThat(geocoder.search("Bursztynowa 2, Warszawa")).isEmpty();
		server.verify();
	}

	@Test
	void skipsResultsWithBrokenCoordinates() {
		server.expect(requestTo(Matchers.startsWith("https://nominatim.test/search")))
				.andRespond(withSuccess("""
						[{"lat":"abc","lon":"21.0","display_name":"zły"},
						 {"lat":"95.0","lon":"21.0","display_name":"poza zakresem"},
						 {"lat":"52.0","lon":"21.0"},
						 {"lat":"52.1","lon":"21.1","display_name":"Dobry, Polska"}]
						""", MediaType.APPLICATION_JSON));

		assertThat(geocoder.search("cokolwiek")).containsExactly(new Geocoder.Place("Dobry", new GeoPoint(52.1, 21.1)));
	}

	@Test
	void failsWhenServiceUnavailable() {
		server.expect(requestTo(Matchers.startsWith("https://nominatim.test/search"))).andRespond(withServiceUnavailable());

		assertThatThrownBy(() -> geocoder.search("Pisz")).isInstanceOf(GeocodingException.class);
	}

}
