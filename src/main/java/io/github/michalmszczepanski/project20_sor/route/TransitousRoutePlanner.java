package io.github.michalmszczepanski.project20_sor.route;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.github.michalmszczepanski.project20_sor.geo.GeoPoint;

/**
 * Trasa komunikacją z Transitous (MOTIS, otwarte rozkłady GTFS — w Polsce m.in. Warszawa, Kraków, Trójmiasto,
 * Wrocław, Poznań, Łódź, GZM, PKP). Warunki: projekt otwarty i niekomercyjny, User-Agent z kontaktem, link do
 * źródeł danych. Pytamy tylko na żądanie, dla jednego wybranego SOR.
 */
@Component
class TransitousRoutePlanner implements RoutePlanner {

	static final int ITINERARIES = 3;

	/** Odjazd minutę temu jeszcze się liczy — zaokrąglenia w rozkładach. */
	static final Duration DEPARTED_TOLERANCE = Duration.ofMinutes(1);

	private final RestClient restClient;

	private final Clock clock;

	TransitousRoutePlanner(RestClient transitousRestClient, Clock clock) {
		this.restClient = transitousRestClient;
		this.clock = clock;
	}

	@Override
	public RouteMode mode() {
		return RouteMode.TRANSIT;
	}

	@Override
	public Optional<Route> plan(GeoPoint from, GeoPoint to) {
		// Pełne sekundy: z ułamkami („…:12.123456Z”) Transitous po cichu ignoruje czas i zwraca połączenia
		// z innego dnia (sprawdzone 05.11.2026).
		Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
		PlanResponse response;
		try {
			response = restClient.get()
					.uri(uri -> uri.path("/api/v5/plan")
							.queryParam("fromPlace", String.format(Locale.ROOT, "%.6f,%.6f", from.lat(), from.lng()))
							.queryParam("toPlace", String.format(Locale.ROOT, "%.6f,%.6f", to.lat(), to.lng()))
							.queryParam("time", now.toString())
							.queryParam("numItineraries", ITINERARIES)
							.build())
					.retrieve()
					.body(PlanResponse.class);
		}
		catch (RestClientException ex) {
			throw new RoutingException("Transitous nie odpowiada: " + ex.getMessage(), ex);
		}
		if (response == null || response.itineraries() == null) {
			return Optional.empty();
		}
		// Najwcześniejszy przyjazd, nie najkrótsza jazda — chory chce być jak najszybciej na miejscu.
		// Połączeń, które już odjechały, nie pokazujemy nawet wtedy, gdy źródło je zwróci.
		Instant departedBefore = now.minus(DEPARTED_TOLERANCE);
		return response.itineraries().stream()
				.filter(it -> it.startTime() != null && it.endTime() != null && it.legs() != null && !it.legs().isEmpty())
				.filter(it -> !it.startTime().isBefore(departedBefore))
				.min(Comparator.comparing(Itinerary::endTime))
				.map(TransitousRoutePlanner::toRoute);
	}

	private static Route toRoute(Itinerary itinerary) {
		List<Route.Leg> legs = itinerary.legs().stream().map(TransitousRoutePlanner::toLeg).toList();
		Instant departure = itinerary.startTime();
		int minutes = (int) Math.ceil(Duration.between(departure, itinerary.endTime()).toSeconds() / 60.0);
		return new Route(RouteMode.TRANSIT, minutes, null, departure, itinerary.endTime(), itinerary.transfers(), legs);
	}

	private static Route.Leg toLeg(Leg leg) {
		List<double[]> path = leg.legGeometry() == null || leg.legGeometry().points() == null
				? List.of()
				: Polyline.decode(leg.legGeometry().points(), leg.legGeometry().precision());
		return new Route.Leg(leg.mode(), leg.routeShortName(), name(leg.from()), name(leg.to()), leg.startTime(),
				leg.endTime(), (int) Math.ceil(leg.duration() / 60.0), path);
	}

	/** Transitous nazywa punkt startu i celu „START”/„END” — to nasze współrzędne, nie przystanki. */
	private static String name(Place place) {
		return place == null || "START".equals(place.name()) || "END".equals(place.name()) ? null : place.name();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record PlanResponse(List<Itinerary> itineraries) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Itinerary(Instant startTime, Instant endTime, int transfers, List<Leg> legs) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Leg(String mode, String routeShortName, Place from, Place to, Instant startTime, Instant endTime,
			double duration, Geometry legGeometry) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Place(String name) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Geometry(String points, int precision) {
	}

}
