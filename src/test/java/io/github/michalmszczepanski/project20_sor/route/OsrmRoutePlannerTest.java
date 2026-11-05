package io.github.michalmszczepanski.project20_sor.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import io.github.michalmszczepanski.project20_sor.geo.GeoPoint;

class OsrmRoutePlannerTest {

	private static final GeoPoint JULIANOW = new GeoPoint(52.0974, 21.0563);

	private static final GeoPoint MIEDZYLESIE = new GeoPoint(52.203859, 21.183758);

	private final RestClient.Builder builder = RestClient.builder().baseUrl("https://osrm.test");

	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

	private final OsrmRoutePlanner planner = new OsrmRoutePlanner(builder.build());

	@Test
	void parsesRealRouteWithGeometry() {
		server.expect(requestTo("https://osrm.test/route/v1/driving/21.056300,52.097400;21.183758,52.203859"
				+ "?overview=full&geometries=geojson"))
				.andRespond(withSuccess(new ClassPathResource("fixtures/osrm-route.json"), MediaType.APPLICATION_JSON));

		Route route = planner.plan(JULIANOW, MIEDZYLESIE).orElseThrow();

		server.verify();
		assertThat(route.mode()).isEqualTo(RouteMode.CAR);
		assertThat(route.minutes()).isEqualTo(32); // 1877,3 s
		assertThat(route.distanceKm()).isEqualTo(25.511); // 25 511 m
		assertThat(route.legs()).hasSize(1);
		// GeoJSON ma [długość, szerokość] — na wyjściu [szerokość, długość]
		assertThat(route.legs().get(0).path()).hasSize(801);
		assertThat(route.legs().get(0).path().get(0)).containsExactly(52.097434, 21.056078);
	}

	@Test
	void noRouteGivesEmpty() {
		server.expect(requestTo(Matchers.startsWith("https://osrm.test/route/")))
				.andRespond(withSuccess("{\"code\":\"NoRoute\",\"routes\":[]}", MediaType.APPLICATION_JSON));

		assertThat(planner.plan(JULIANOW, MIEDZYLESIE)).isEmpty();
	}

	@Test
	void serviceErrorIsRoutingException() {
		server.expect(requestTo(Matchers.startsWith("https://osrm.test/route/"))).andRespond(withServiceUnavailable());

		assertThatThrownBy(() -> planner.plan(JULIANOW, MIEDZYLESIE)).isInstanceOf(RoutingException.class);
	}

}
