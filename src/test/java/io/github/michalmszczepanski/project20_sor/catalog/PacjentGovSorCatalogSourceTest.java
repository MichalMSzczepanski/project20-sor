package io.github.michalmszczepanski.project20_sor.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

class PacjentGovSorCatalogSourceTest {

	private static final String PATH = "/sites/pacjent/files/dane/Mapy-SOR-24.09-2026.json";

	private static final String NEWER_PATH = "/sites/pacjent/files/dane/Mapy-SOR-05.11-2026.json";

	@Test
	void mapsValidEntriesAndSkipsBrokenOnes() {
		// Serwer plików może nie podać charsetu — polskie znaki muszą przejść mimo to.
		List<SorUnit> units = fetchWith(MediaType.APPLICATION_OCTET_STREAM);

		assertThat(units).extracting(SorUnit::queueId).containsExactly("000000025802-079", "000000001137-014", "000000000001-001");
		SorUnit lubin = units.get(0);
		assertThat(lubin.hospitalName()).isEqualTo("REGIONALNE CENTRUM ZDROWIA SPÓŁKA Z OGRANICZONĄ ODPOWIEDZIALNOŚCIĄ");
		assertThat(lubin.address().formatted()).isEqualTo("Gen.Józefa Bema 5/6, 59-300 Lubin");
		assertThat(lubin.phone()).isEqualTo("76 840 15 81");
		assertThat(lubin.notes()).isEqualTo("całodobowo");
		assertThat(lubin.voivodeship()).isEqualTo("Dolnośląskie");
		assertThat(lubin.location().lat()).isEqualTo(51.40065);
		assertThat(lubin.location().lng()).isEqualTo(16.19794);
	}

	@Test
	void repairsCoordinatesGivenAsTextWithTrailingComma() {
		SorUnit unit = fetchWith(MediaType.APPLICATION_JSON).get(2);

		assertThat(unit.location().lat()).isEqualTo(52.2588);
		assertThat(unit.location().lng()).isEqualTo(19.1853588);
	}

	@Test
	void trimsQueueId() {
		assertThat(fetchWith(MediaType.APPLICATION_JSON).get(1).queueId()).isEqualTo("000000001137-014");
	}

	@Test
	void usesFileFoundByLocator() {
		SorCatalogData data = fetch(() -> Optional.of(NEWER_PATH), NEWER_PATH, MediaType.APPLICATION_JSON);

		assertThat(data.source()).isEqualTo(NEWER_PATH);
		assertThat(data.units()).hasSize(3);
	}

	@Test
	void fallsBackToConfiguredPathWhenLocatorFails() {
		SorCatalogData data = fetch(Optional::empty, PATH, MediaType.APPLICATION_JSON);

		assertThat(data.source()).isEqualTo(PATH);
	}

	private static List<SorUnit> fetchWith(MediaType contentType) {
		return fetch(() -> Optional.of(PATH), PATH, contentType).units();
	}

	private static SorCatalogData fetch(CatalogFileLocator locator, String expectedPath, MediaType contentType) {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://pacjent.gov.pl");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://pacjent.gov.pl" + expectedPath))
				.andRespond(withSuccess(new ClassPathResource("fixtures/mapy-sor-sample.json"), contentType));
		SorCatalogData data = new PacjentGovSorCatalogSource(builder.build(), JsonMapper.builder().build(), locator,
				PATH)
				.fetch();
		server.verify();
		return data;
	}

}
