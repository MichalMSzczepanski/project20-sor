package io.github.michalmszczepanski.project20_sor.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

class PacjentGovCatalogFileLocatorTest {

	private static final String PAGE = "/szpitalny-oddzial-ratunkowy-sor";

	private MockRestServiceServer server;

	private PacjentGovCatalogFileLocator locator;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder()
				.baseUrl("https://pacjent.gov.pl")
				.defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE);
		server = MockRestServiceServer.bindTo(builder).build();
		locator = new PacjentGovCatalogFileLocator(builder.build(), JsonMapper.builder().build(), PAGE);
	}

	@Test
	void readsFileFromDrupalSettingsOfRealPage() {
		server.expect(requestTo("https://pacjent.gov.pl" + PAGE))
				.andExpect(header("Accept", MediaType.TEXT_HTML_VALUE))
				.andRespond(withSuccess(new ClassPathResource("fixtures/pacjent-sor-page.html"), MediaType.TEXT_HTML));

		assertThat(locator.locate()).hasValue("/sites/pacjent/files/dane/Mapy-SOR-24.09-2026.json");
		server.verify();
	}

	@Test
	void ignoresOtherMapFilesAndDuplicates() {
		respondWith(settings("""
				{"cez_maps":{"maps":{
				  "1":{"map_points_file":"\\/sites\\/pacjent\\/files\\/dane\\/Mapy-POZ-01.01-2026.json"},
				  "2":{"map_points_file":"\\/sites\\/pacjent\\/files\\/dane\\/Mapy-SOR-05.11-2026.json"},
				  "3":{"map_points_file":"\\/sites\\/pacjent\\/files\\/dane\\/Mapy-SOR-05.11-2026.json"},
				  "4":{"map_points_file":null}}}}
				"""));

		assertThat(locator.locate()).hasValue("/sites/pacjent/files/dane/Mapy-SOR-05.11-2026.json");
	}

	@Test
	void emptyWhenPageHasNoSettings() {
		respondWith("<html><body>przerwa techniczna</body></html>");

		assertThat(locator.locate()).isEmpty();
	}

	@Test
	void emptyWhenSettingsHaveNoSorFile() {
		respondWith(settings("""
				{"cez_maps":{"maps":{"1":{"map_points_file":"\\/sites\\/pacjent\\/files\\/dane\\/Mapy-POZ.json"}}}}
				"""));

		assertThat(locator.locate()).isEmpty();
	}

	@Test
	void emptyWhenSettingsAreBrokenJson() {
		respondWith(settings("{\"cez_maps\":"));

		assertThat(locator.locate()).isEmpty();
	}

	@Test
	void emptyWhenPageUnavailable() {
		server.expect(requestTo("https://pacjent.gov.pl" + PAGE)).andRespond(withServiceUnavailable());

		assertThat(locator.locate()).isEqualTo(Optional.empty());
	}

	private void respondWith(String html) {
		server.expect(requestTo("https://pacjent.gov.pl" + PAGE)).andRespond(withSuccess(html, MediaType.TEXT_HTML));
	}

	private static String settings(String json) {
		return "<html><body><script type=\"application/json\" data-drupal-selector=\"drupal-settings-json\">" + json
				+ "</script></body></html>";
	}

}
