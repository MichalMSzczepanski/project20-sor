package io.github.michalmszczepanski.project20_sor.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OsrmTravelEstimatorTest {

	private static final GeoPoint ORIGIN = new GeoPoint(52.06, 21.03);

	private static final List<GeoPoint> DESTINATIONS = List.of(new GeoPoint(52.2205, 21.1569), new GeoPoint(51.59, 21.55));

	private MockRestServiceServer server;

	private OsrmTravelEstimator estimator;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://osrm.test");
		server = MockRestServiceServer.bindTo(builder).build();
		estimator = new OsrmTravelEstimator(builder.build(), new StraightLineTravelEstimator(1.3, 50));
	}

	@Test
	void usesRoadDurationsFromRealResponse() {
		server.expect(requestTo("https://osrm.test/table/v1/driving/"
				+ "21.030000,52.060000;21.156900,52.220500;21.550000,51.590000"
				+ "?sources=0&annotations=duration,distance"))
				.andRespond(withSuccess(new ClassPathResource("fixtures/osrm-table.json"), MediaType.APPLICATION_JSON));

		List<TravelEstimate> estimates = estimator.estimate(ORIGIN, DESTINATIONS);

		server.verify();
		assertThat(estimates).containsExactly(
				new TravelEstimate(27.2684, 35, TravelEstimate.Method.ROAD),
				new TravelEstimate(69.1318, 72, TravelEstimate.Method.ROAD));
	}

	@Test
	void unreachableDestinationFallsBackToStraightLine() {
		respondWith("""
				{"code":"Ok","durations":[[0,600.0,null]],"distances":[[0,9000.0,null]]}
				""");

		List<TravelEstimate> estimates = estimator.estimate(ORIGIN, DESTINATIONS);

		assertThat(estimates).extracting(TravelEstimate::method)
				.containsExactly(TravelEstimate.Method.ROAD, TravelEstimate.Method.STRAIGHT_LINE);
	}

	@Test
	void serverErrorFallsBackToStraightLine() {
		server.expect(requestTo(Matchers.startsWith("https://osrm.test/"))).andRespond(withServerError());

		assertThat(estimator.estimate(ORIGIN, DESTINATIONS)).extracting(TravelEstimate::method)
				.containsOnly(TravelEstimate.Method.STRAIGHT_LINE);
	}

	@Test
	void errorCodeOrWrongSizeFallsBackToStraightLine() {
		respondWith("""
				{"code":"InvalidQuery","message":"Too many table coordinates"}
				""");
		assertThat(estimator.estimate(ORIGIN, DESTINATIONS)).extracting(TravelEstimate::method)
				.containsOnly(TravelEstimate.Method.STRAIGHT_LINE);

		server.reset();
		respondWith("""
				{"code":"Ok","durations":[[0,600.0]],"distances":[[0,9000.0]]}
				""");
		assertThat(estimator.estimate(ORIGIN, DESTINATIONS)).extracting(TravelEstimate::method)
				.containsOnly(TravelEstimate.Method.STRAIGHT_LINE);
	}

	@Test
	void noDestinationsMeansNoRequest() {
		assertThat(estimator.estimate(ORIGIN, List.of())).isEmpty();
		server.verify();
	}

	private void respondWith(String json) {
		server.expect(requestTo(Matchers.startsWith("https://osrm.test/")))
				.andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
	}

}
