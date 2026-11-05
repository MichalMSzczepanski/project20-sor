package io.github.michalmszczepanski.project20_sor.web;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.michalmszczepanski.project20_sor.geo.GeoPoint;
import io.github.michalmszczepanski.project20_sor.geo.Geocoder;
import io.github.michalmszczepanski.project20_sor.geo.GeocodingException;

@WebMvcTest(GeocodeController.class)
class GeocodeControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private Geocoder geocoder;

	@Test
	void returnsPlaces() throws Exception {
		given(geocoder.search("Pisz")).willReturn(List.of(new Geocoder.Place("Pisz, powiat piski", new GeoPoint(53.629, 21.808))));

		mvc.perform(get("/api/geocode").param("q", "  Pisz "))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].label").value("Pisz, powiat piski"))
				.andExpect(jsonPath("$[0].lat").value(53.629))
				.andExpect(jsonPath("$[0].lng").value(21.808));
	}

	@Test
	void rejectsTooShortOrTooLongQuery() throws Exception {
		mvc.perform(get("/api/geocode").param("q", " ab ")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/geocode").param("q", "x".repeat(201))).andExpect(status().isBadRequest());
		mvc.perform(get("/api/geocode")).andExpect(status().isBadRequest());
	}

	@Test
	void serviceUnavailableWhenGeocoderFails() throws Exception {
		given(geocoder.search("Pisz")).willThrow(new GeocodingException("HTTP 503", null));

		mvc.perform(get("/api/geocode").param("q", "Pisz"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Wyszukiwarka adresów")));
	}

}
