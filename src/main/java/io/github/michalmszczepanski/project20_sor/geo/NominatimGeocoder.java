package io.github.michalmszczepanski.project20_sor.geo;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.michalmszczepanski.project20_sor.config.SorProperties;

/**
 * Wyszukiwanie adresów w Nominatim (OpenStreetMap), tylko w Polsce. Zasady publicznego serwera:
 * najwyżej 1 zapytanie/s, identyfikujący User-Agent, bez podpowiedzi przy każdym naciśnięciu klawisza —
 * dlatego pytamy z backendu, z limitem i pamięcią podręczną, a interfejs szuka dopiero po zatwierdzeniu.
 */
@Component
class NominatimGeocoder implements Geocoder {

	static final int MAX_RESULTS = 5;

	private final RestClient restClient;

	private final MinIntervalThrottle throttle;

	@Autowired
	NominatimGeocoder(RestClient nominatimRestClient, SorProperties properties, Clock clock) {
		this(nominatimRestClient, new MinIntervalThrottle(properties.geocoding().minInterval(), clock,
				duration -> Thread.sleep(duration.toMillis())));
	}

	NominatimGeocoder(RestClient restClient, MinIntervalThrottle throttle) {
		this.restClient = restClient;
		this.throttle = throttle;
	}

	@Override
	public List<Place> search(String query) {
		Result[] results;
		try {
			throttle.acquire();
			results = restClient.get()
					.uri(uri -> uri.path("/search")
							.queryParam("q", query)
							.queryParam("format", "jsonv2")
							.queryParam("countrycodes", "pl")
							.queryParam("accept-language", "pl")
							.queryParam("limit", MAX_RESULTS)
							.queryParam("addressdetails", 1)
							.build())
					.retrieve()
					.body(Result[].class);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new GeocodingException("Przerwano wyszukiwanie adresu", ex);
		}
		catch (RestClientException ex) {
			throw new GeocodingException("Wyszukiwarka adresów nie odpowiada: " + ex.getMessage(), ex);
		}
		if (results == null) {
			return List.of();
		}
		// Ten sam adres potrafi wrócić kilka razy (np. budynek i jego wejście) — zostawiamy pierwszy.
		Map<String, Place> byLabel = new LinkedHashMap<>();
		Arrays.stream(results).map(NominatimGeocoder::toPlace).filter(Objects::nonNull)
				.forEach(place -> byLabel.putIfAbsent(place.label(), place));
		return List.copyOf(byLabel.values());
	}

	private static Place toPlace(Result result) {
		if (result == null || result.displayName() == null || result.lat() == null || result.lon() == null) {
			return null;
		}
		try {
			GeoPoint location = new GeoPoint(Double.parseDouble(result.lat()), Double.parseDouble(result.lon()));
			return new Place(label(result), location);
		}
		catch (IllegalArgumentException ex) {
			// nieczytelne albo spoza zakresu współrzędne — pomijamy ten wynik
			return null;
		}
	}

	/**
	 * Czytelna etykieta z części adresu: „Międzyleski Szpital Specjalistyczny, Bursztynowa 2, Wawer, Warszawa”,
	 * a poza miastami z powiatem i województwem, żeby odróżnić miejscowości o tej samej nazwie.
	 */
	static String label(Result result) {
		Map<String, String> a = result.address() == null ? Map.of() : result.address();
		String locality = first(a, "city", "town", "village", "hamlet");
		String street = a.get("road") == null ? null
				: a.get("house_number") == null ? a.get("road") : a.get("road") + " " + a.get("house_number");
		List<String> parts = new ArrayList<>();
		addDistinct(parts, result.name());
		addDistinct(parts, street);
		if (a.containsKey("city")) {
			addDistinct(parts, a.get("suburb"));
			addDistinct(parts, locality);
		}
		else {
			addDistinct(parts, locality);
			if (a.containsKey("hamlet")) {
				addDistinct(parts, a.get("village"));
			}
			addDistinct(parts, a.get("county"));
			addDistinct(parts, a.get("state") == null ? null : a.get("state").replaceFirst("^województwo\\s+", ""));
		}
		return parts.isEmpty() ? result.displayName().replaceFirst(",\\s*Polska$", "") : String.join(", ", parts);
	}

	private static String first(Map<String, String> address, String... keys) {
		return Arrays.stream(keys).map(address::get).filter(Objects::nonNull).findFirst().orElse(null);
	}

	private static void addDistinct(List<String> parts, String part) {
		if (part != null && !part.isBlank() && !parts.contains(part.trim())) {
			parts.add(part.trim());
		}
	}

	/** Nominatim podaje współrzędne jako tekst. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Result(String lat, String lon, @JsonProperty("display_name") String displayName, String name,
			Map<String, String> address) {
	}

}
