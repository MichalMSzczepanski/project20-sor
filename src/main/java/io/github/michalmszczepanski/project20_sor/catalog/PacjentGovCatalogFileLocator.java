package io.github.michalmszczepanski.project20_sor.catalog;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import io.github.michalmszczepanski.project20_sor.config.SorProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Czyta ścieżkę pliku z listą SOR ze strony pacjent.gov.pl: mapa dostaje ją w ustawieniach Drupala
 * ({@code drupalSettings.cez_maps.maps.<id>.map_points_file}, potwierdzone na stronie z 14.10.2026).
 * Ustawienia czytamy jako JSON — parser sam rozkoduje {@code \/} i inne sekwencje.
 */
@Component
class PacjentGovCatalogFileLocator implements CatalogFileLocator {

	private static final Logger log = LoggerFactory.getLogger(PacjentGovCatalogFileLocator.class);

	private static final Pattern DRUPAL_SETTINGS = Pattern.compile(
			"<script[^>]*data-drupal-selector=\"drupal-settings-json\"[^>]*>(.*?)</script>", Pattern.DOTALL);

	private static final Pattern SOR_FILE = Pattern.compile("/sites/pacjent/files/[^\"]*/Mapy-SOR[^/\"]*\\.json");

	private final RestClient restClient;

	private final JsonMapper jsonMapper;

	private final String pagePath;

	@Autowired
	PacjentGovCatalogFileLocator(RestClient pacjentGovRestClient, JsonMapper jsonMapper, SorProperties properties) {
		this(pacjentGovRestClient, jsonMapper, properties.catalog().pagePath());
	}

	PacjentGovCatalogFileLocator(RestClient restClient, JsonMapper jsonMapper, String pagePath) {
		this.restClient = restClient;
		this.jsonMapper = jsonMapper;
		this.pagePath = pagePath;
	}

	@Override
	public Optional<String> locate() {
		byte[] page;
		try {
			page = restClient.get().uri(pagePath).accept(MediaType.TEXT_HTML).retrieve()
					.body(byte[].class);
		}
		catch (RestClientException ex) {
			log.warn("Nie udało się pobrać strony z mapą SOR {}: {}", pagePath, ex.getMessage());
			return Optional.empty();
		}
		if (page == null) {
			return Optional.empty();
		}
		Matcher settings = DRUPAL_SETTINGS.matcher(new String(page, StandardCharsets.UTF_8));
		if (!settings.find()) {
			log.warn("Na stronie {} nie ma ustawień drupalSettings — zmienił się układ strony?", pagePath);
			return Optional.empty();
		}
		List<String> files;
		try {
			files = jsonMapper.readTree(settings.group(1))
					.findValues("map_points_file")
					.stream()
					.filter(JsonNode::isString)
					.map(JsonNode::stringValue)
					.filter(path -> SOR_FILE.matcher(path).matches())
					.distinct()
					.toList();
		}
		catch (JacksonException ex) {
			log.warn("Nieczytelne ustawienia drupalSettings na stronie {}: {}", pagePath, ex.getOriginalMessage());
			return Optional.empty();
		}
		if (files.isEmpty()) {
			log.warn("Na stronie {} nie ma pliku z listą SOR (map_points_file)", pagePath);
			return Optional.empty();
		}
		if (files.size() > 1) {
			log.warn("Na stronie {} jest kilka plików z listą SOR {} — biorę pierwszy", pagePath, files);
		}
		return Optional.of(files.get(0));
	}

}
