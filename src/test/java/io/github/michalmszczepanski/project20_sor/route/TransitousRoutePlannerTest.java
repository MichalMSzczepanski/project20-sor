package io.github.michalmszczepanski.project20_sor.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import io.github.michalmszczepanski.project20_sor.geo.GeoPoint;

class TransitousRoutePlannerTest {

	/** Z ułamkami sekund — jak z prawdziwego zegara. */
	private static final Instant NOW = Instant.parse("2026-11-05T20:10:00.123456Z");

	private static final GeoPoint JULIANOW = new GeoPoint(52.0974, 21.0563);

	private static final GeoPoint MIEDZYLESIE = new GeoPoint(52.2205, 21.1569);

	private final RestClient.Builder builder = RestClient.builder().baseUrl("https://transitous.test");

	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

	private final TransitousRoutePlanner planner = new TransitousRoutePlanner(builder.build(),
			Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void picksEarliestArrivalFromRealPlan() {
		server.expect(requestTo(Matchers.startsWith("https://transitous.test/api/v5/plan")))
				.andExpect(queryParam("fromPlace", "52.097400,21.056300"))
				.andExpect(queryParam("toPlace", "52.220500,21.156900"))
				.andExpect(queryParam("time", "2026-11-05T20:10:00Z"))
				.andRespond(withSuccess(new ClassPathResource("fixtures/transitous-plan.json"), MediaType.APPLICATION_JSON));

		Route route = planner.plan(JULIANOW, MIEDZYLESIE).orElseThrow();

		server.verify();
		// Prawdziwa odpowiedź z 05.11.2026: autobus 739 → metro M1 → autobus 502, przyjazd 21:43 UTC
		assertThat(route.mode()).isEqualTo(RouteMode.TRANSIT);
		assertThat(route.departure()).isEqualTo(Instant.parse("2026-11-05T20:18:00Z"));
		assertThat(route.arrival()).isEqualTo(Instant.parse("2026-11-05T21:43:00Z"));
		assertThat(route.minutes()).isEqualTo(85);
		assertThat(route.transfers()).isEqualTo(2);
		assertThat(route.legs()).extracting(Route.Leg::mode)
				.containsExactly("WALK", "BUS", "WALK", "SUBWAY", "WALK", "BUS", "WALK");
		assertThat(route.legs()).extracting(Route.Leg::line).containsSubsequence("739", "M1", "502");
		Route.Leg bus = route.legs().get(1);
		assertThat(bus.from()).isEqualTo("Józefosław Julianów 02");
		assertThat(bus.path()).isNotEmpty();
		// punkty na trasie autobusu leżą między Julianowem a metrem Wilanowska
		assertThat(bus.path()).allSatisfy(p -> assertThat(p[0]).isBetween(52.05, 52.25));
		// START/END to nasze współrzędne, nie przystanki
		assertThat(route.legs().get(0).from()).isNull();
	}

	@Test
	void skipsConnectionsThatAlreadyDeparted() {
		server.expect(requestTo(Matchers.startsWith("https://transitous.test/api/v5/plan")))
				.andRespond(withSuccess("""
						{"itineraries":[
						  {"startTime":"2026-11-04T03:11:00Z","endTime":"2026-11-04T04:40:00Z","transfers":1,
						   "legs":[{"mode":"BUS","duration":5340,"legGeometry":{"points":"","precision":6}}]},
						  {"startTime":"2026-11-05T20:38:00Z","endTime":"2026-11-05T22:26:00Z","transfers":3,
						   "legs":[{"mode":"BUS","duration":6480,"legGeometry":{"points":"","precision":6}}]}]}
						""", MediaType.APPLICATION_JSON));

		Route route = planner.plan(JULIANOW, MIEDZYLESIE).orElseThrow();

		// Wczorajsze połączenie ma najwcześniejszy przyjazd, ale już odjechało — wybieramy dzisiejsze.
		assertThat(route.departure()).isEqualTo(Instant.parse("2026-11-05T20:38:00Z"));
	}

	@Test
	void onlyDepartedConnectionsGiveEmpty() {
		server.expect(requestTo(Matchers.startsWith("https://transitous.test/api/v5/plan")))
				.andRespond(withSuccess("""
						{"itineraries":[{"startTime":"2026-11-04T03:11:00Z","endTime":"2026-11-04T04:40:00Z",
						  "transfers":1,"legs":[{"mode":"BUS","duration":5340}]}]}
						""", MediaType.APPLICATION_JSON));

		assertThat(planner.plan(JULIANOW, MIEDZYLESIE)).isEmpty();
	}

	@Test
	void noItinerariesGivesEmpty() {
		server.expect(requestTo(Matchers.startsWith("https://transitous.test/api/v5/plan")))
				.andRespond(withSuccess("{\"itineraries\":[],\"direct\":[]}", MediaType.APPLICATION_JSON));

		assertThat(planner.plan(JULIANOW, MIEDZYLESIE)).isEmpty();
	}

	@Test
	void serviceErrorIsRoutingException() {
		server.expect(requestTo(Matchers.startsWith("https://transitous.test/"))).andRespond(withServiceUnavailable());

		assertThatThrownBy(() -> planner.plan(JULIANOW, MIEDZYLESIE)).isInstanceOf(RoutingException.class);
	}

}
