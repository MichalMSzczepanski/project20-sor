package io.github.michalmszczepanski.project20_sor.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import io.github.michalmszczepanski.project20_sor.config.SorProperties;
import io.github.michalmszczepanski.project20_sor.geo.GeoPoint;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
class PacjentGovSorCatalogSource implements SorCatalogSource {

	private static final Logger log = LoggerFactory.getLogger(PacjentGovSorCatalogSource.class);

	static final Pattern QUEUE_ID = Pattern.compile("\\d{12}-\\d{3}");

	private final RestClient restClient;

	private final JsonMapper jsonMapper;

	private final CatalogFileLocator locator;

	private final String fallbackPath;

	@Autowired
	PacjentGovSorCatalogSource(RestClient pacjentGovRestClient, JsonMapper jsonMapper, CatalogFileLocator locator,
			SorProperties properties) {
		this(pacjentGovRestClient, jsonMapper, locator, properties.catalog().path());
	}

	PacjentGovSorCatalogSource(RestClient restClient, JsonMapper jsonMapper, CatalogFileLocator locator,
			String fallbackPath) {
		this.restClient = restClient;
		this.jsonMapper = jsonMapper;
		this.locator = locator;
		this.fallbackPath = fallbackPath;
	}

	@Override
	public SorCatalogData fetch() {
		String path = locator.locate().orElseGet(() -> {
			log.warn("Nie ustalono aktualnego pliku z listą SOR — używam ścieżki z konfiguracji {}", fallbackPath);
			return fallbackPath;
		});
		return new SorCatalogData(path, fetchUnits(path));
	}

	private List<SorUnit> fetchUnits(String path) {
		// Bajty, nie String: serwer plików nie musi podawać charsetu, a Jackson sam wykrywa UTF-8.
		byte[] body = restClient.get().uri(path).retrieve().body(byte[].class);
		if (body == null) {
			throw new IllegalStateException("Pusta odpowiedź z listą SOR: " + path);
		}
		JsonNode root = jsonMapper.readTree(body);
		if (!root.isArray()) {
			throw new IllegalStateException("Lista SOR nie jest tablicą JSON: " + path);
		}
		// Każdy wpis osobno — jeden zepsuty wpis w źródle nie może wywrócić całej listy.
		List<SorUnit> units = new ArrayList<>(root.size());
		for (JsonNode node : root.values()) {
			SorUnit unit = toUnit(node);
			if (unit != null) {
				units.add(unit);
			}
		}
		return units;
	}

	private SorUnit toUnit(JsonNode node) {
		PacjentGovSorEntry e;
		try {
			e = jsonMapper.treeToValue(node, PacjentGovSorEntry.class);
		}
		catch (JacksonException ex) {
			log.warn("Pomijam nieczytelny wpis listy SOR ({}): {}", ex.getOriginalMessage(), node);
			return null;
		}
		String queueId = text(e.bookNumber());
		OptionalDouble lat = CoordinateParser.parse(e.lat());
		OptionalDouble lng = CoordinateParser.parse(e.lng());
		if (!QUEUE_ID.matcher(queueId).matches() || lat.isEmpty() || lng.isEmpty()) {
			log.warn("Pomijam wpis listy SOR bez poprawnego numeru księgi lub współrzędnych: {}", node);
			return null;
		}
		GeoPoint location;
		try {
			location = new GeoPoint(lat.getAsDouble(), lng.getAsDouble());
		}
		catch (IllegalArgumentException ex) {
			log.warn("Pomijam wpis listy SOR ze współrzędnymi poza zakresem: {}", node);
			return null;
		}
		SorUnit.Address address = new SorUnit.Address(text(e.street()), text(e.buildingNo()), text(e.localNo()),
				text(e.postalCode()), text(e.city()));
		return new SorUnit(queueId, text(e.hospitalName()), text(e.unitName()), address, text(e.voivodeship()),
				text(e.phone()), text(e.notes()), location);
	}

	private static String text(String value) {
		return Objects.requireNonNullElse(value, "").trim();
	}

}
