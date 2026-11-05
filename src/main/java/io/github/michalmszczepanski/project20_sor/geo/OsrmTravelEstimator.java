package io.github.michalmszczepanski.project20_sor.geo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Czas przejazdu po drogach z usługi table OSRM — jedno zapytanie na wszystkie cele.
 * OSRM nie zna korków. Gdy usługa nie odpowie albo nie znajdzie trasy, wracamy do linii prostej.
 */
@Component
@Primary
@ConditionalOnBooleanProperty("sor.travel.osrm.enabled")
class OsrmTravelEstimator implements TravelEstimator {

	private static final Logger log = LoggerFactory.getLogger(OsrmTravelEstimator.class);

	private final RestClient restClient;

	private final StraightLineTravelEstimator fallback;

	OsrmTravelEstimator(RestClient osrmRestClient, StraightLineTravelEstimator fallback) {
		this.restClient = osrmRestClient;
		this.fallback = fallback;
	}

	@Override
	public List<TravelEstimate> estimate(GeoPoint origin, List<GeoPoint> destinations) {
		if (destinations.isEmpty()) {
			return List.of();
		}
		TableResponse response;
		try {
			response = restClient.get()
					.uri("/table/v1/driving/" + coordinates(origin, destinations)
							+ "?sources=0&annotations=duration,distance")
					.retrieve()
					.body(TableResponse.class);
		}
		catch (RestClientException ex) {
			log.warn("OSRM niedostępny ({}) — dojazd z linii prostej", ex.getMessage());
			return fallback.estimate(origin, destinations);
		}
		if (response == null || !"Ok".equals(response.code()) || !hasRow(response, destinations.size())) {
			log.warn("OSRM zwrócił niepoprawną odpowiedź ({}) — dojazd z linii prostej",
					response == null ? "pusta" : response.code());
			return fallback.estimate(origin, destinations);
		}
		List<Double> durations = response.durations().get(0);
		List<Double> distances = response.distances().get(0);
		List<TravelEstimate> estimates = new ArrayList<>(destinations.size());
		for (int i = 0; i < destinations.size(); i++) {
			// Indeks 0 to punkt startowy (sources=0), cele zaczynają się od 1.
			Double seconds = durations.get(i + 1);
			Double meters = distances.get(i + 1);
			estimates.add(seconds == null || meters == null
					? fallback.estimate(origin, destinations.get(i))
					: new TravelEstimate(meters / 1000, (int) Math.ceil(seconds / 60), TravelEstimate.Method.ROAD));
		}
		return estimates;
	}

	/** OSRM przyjmuje „lng,lat” rozdzielone średnikami; punkt startowy pierwszy. */
	private static String coordinates(GeoPoint origin, List<GeoPoint> destinations) {
		return Stream.concat(Stream.of(origin), destinations.stream())
				.map(p -> String.format(Locale.ROOT, "%.6f,%.6f", p.lng(), p.lat()))
				.collect(Collectors.joining(";"));
	}

	private static boolean hasRow(TableResponse response, int destinations) {
		return response.durations() != null && response.distances() != null && !response.durations().isEmpty()
				&& !response.distances().isEmpty() && response.durations().get(0).size() == destinations + 1
				&& response.distances().get(0).size() == destinations + 1;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TableResponse(String code, List<List<Double>> durations, List<List<Double>> distances) {
	}

}
