package io.github.michalmszczepanski.project20_sor.route;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.github.michalmszczepanski.project20_sor.geo.GeoPoint;

/** Trasa samochodem z usługi route OSRM, z pełnym przebiegiem drogi (GeoJSON). Bez korków. */
@Component
@ConditionalOnBooleanProperty("sor.travel.osrm.enabled")
class OsrmRoutePlanner implements RoutePlanner {

	private final RestClient restClient;

	OsrmRoutePlanner(RestClient osrmRestClient) {
		this.restClient = osrmRestClient;
	}

	@Override
	public RouteMode mode() {
		return RouteMode.CAR;
	}

	@Override
	public Optional<Route> plan(GeoPoint from, GeoPoint to) {
		RouteResponse response;
		try {
			response = restClient.get()
					.uri(String.format(Locale.ROOT, "/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
							from.lng(), from.lat(), to.lng(), to.lat()))
					.retrieve()
					.body(RouteResponse.class);
		}
		catch (RestClientException ex) {
			throw new RoutingException("OSRM nie odpowiada: " + ex.getMessage(), ex);
		}
		if (response == null || !"Ok".equals(response.code()) || response.routes() == null || response.routes().isEmpty()) {
			return Optional.empty();
		}
		OsrmRoute route = response.routes().get(0);
		// GeoJSON podaje [długość, szerokość]; na mapę oddajemy [szerokość, długość].
		List<double[]> path = route.geometry().coordinates().stream().map(c -> new double[] { c[1], c[0] }).toList();
		int minutes = (int) Math.ceil(route.duration() / 60);
		return Optional.of(new Route(RouteMode.CAR, minutes, route.distance() / 1000, null, null, 0,
				List.of(new Route.Leg("CAR", null, null, null, null, null, minutes, path))));
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RouteResponse(String code, List<OsrmRoute> routes) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record OsrmRoute(double duration, double distance, Geometry geometry) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Geometry(List<double[]> coordinates) {
	}

}
