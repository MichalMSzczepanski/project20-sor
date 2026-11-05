package io.github.michalmszczepanski.project20_sor.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.github.michalmszczepanski.project20_sor.geo.GeoPoint;
import io.github.michalmszczepanski.project20_sor.route.Route;
import io.github.michalmszczepanski.project20_sor.route.RouteMode;
import io.github.michalmszczepanski.project20_sor.route.RouteService;
import io.github.michalmszczepanski.project20_sor.route.RoutingException;

@WebMvcTest(RouteController.class)
class RouteControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private RouteService routeService;

	@Test
	void returnsCarRouteWithPath() throws Exception {
		Route route = new Route(RouteMode.CAR, 32, 25.5, null, null, 0,
				List.of(new Route.Leg("CAR", null, null, null, null, null, 32,
						List.of(new double[] { 52.1, 21.05 }, new double[] { 52.2, 21.18 }))));
		given(routeService.plan(any(GeoPoint.class), any(GeoPoint.class), eq(RouteMode.CAR))).willReturn(Optional.of(route));

		mvc.perform(request("CAR"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.minutes").value(32))
				.andExpect(jsonPath("$.distanceKm").value(25.5))
				.andExpect(jsonPath("$.legs[0].path[1][0]").value(52.2))
				.andExpect(jsonPath("$.legs[0].path[1][1]").value(21.18));
	}

	@Test
	void notFoundWhenNoTransitConnection() throws Exception {
		given(routeService.plan(any(GeoPoint.class), any(GeoPoint.class), eq(RouteMode.TRANSIT))).willReturn(Optional.empty());

		mvc.perform(request("TRANSIT"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("komunikacją")));
	}

	@Test
	void serviceUnavailableWhenRoutingFails() throws Exception {
		given(routeService.plan(any(GeoPoint.class), any(GeoPoint.class), eq(RouteMode.TRANSIT)))
				.willThrow(new RoutingException("timeout", null));

		mvc.perform(request("TRANSIT")).andExpect(status().isServiceUnavailable());
	}

	@Test
	void rejectsUnknownModeAndBadCoordinates() throws Exception {
		mvc.perform(request("ROWER")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/route").param("fromLat", "95").param("fromLng", "21").param("toLat", "52")
				.param("toLng", "21").param("mode", "CAR")).andExpect(status().isBadRequest());
	}

	private static MockHttpServletRequestBuilder request(String mode) {
		return get("/api/route").param("fromLat", "52.0974").param("fromLng", "21.0563").param("toLat", "52.2039")
				.param("toLng", "21.1838").param("mode", mode);
	}

}
