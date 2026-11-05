package io.github.michalmszczepanski.project20_sor.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

class PacjentGovSorCatalogSourceTest {

	private static final String PATH = "/sites/pacjent/files/dane/Mapy-SOR-24.09-2026.json";

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

	private static List<SorUnit> fetchWith(MediaType contentType) {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://pacjent.gov.pl");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://pacjent.gov.pl" + PATH))
				.andRespond(withSuccess(new ClassPathResource("fixtures/mapy-sor-sample.json"), contentType));
		List<SorUnit> units = new PacjentGovSorCatalogSource(builder.build(), JsonMapper.builder().build(), PATH)
				.fetchAll();
		server.verify();
		return units;
	}

}
