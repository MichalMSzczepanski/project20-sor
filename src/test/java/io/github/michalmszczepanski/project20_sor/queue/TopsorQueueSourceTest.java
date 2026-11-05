package io.github.michalmszczepanski.project20_sor.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

class TopsorQueueSourceTest {

	private static final String QUEUE_ID = "000000001137-014";

	private static final Instant NOW = Instant.ofEpochSecond(1793900280);

	private MockRestServiceServer server;

	private TopsorQueueSource source;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://pacjent.gov.pl");
		server = MockRestServiceServer.bindTo(builder).build();
		source = new TopsorQueueSource(builder.build(), JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void parsesRealResponseAndSendsCacheBustingTimestamp() {
		server.expect(requestTo("https://pacjent.gov.pl/api/v1/datatopsor/" + QUEUE_ID + "?_format=json&t=1793900280"))
				.andRespond(withSuccess(new ClassPathResource("fixtures/topsor-" + QUEUE_ID + ".json"),
						MediaType.APPLICATION_JSON));

		QueueSnapshot snapshot = source.fetch(QUEUE_ID);

		server.verify();
		assertThat(snapshot.queueId()).isEqualTo(QUEUE_ID);
		assertThat(snapshot.generatedAt()).isEqualTo(Instant.ofEpochSecond(1793900278));
		assertThat(snapshot.fetchedAt()).isEqualTo(NOW);
		assertThat(snapshot.patientsPresent()).isEqualTo(18);
		assertThat(snapshot.stages()).hasSize(QueueStage.values().length);
		assertThat(snapshot.otherStages()).isEmpty();
		QueueSnapshot.Stage registration = snapshot.stage(QueueStage.REGISTRATION).orElseThrow();
		assertThat(registration.patients()).isEqualTo(3);
		assertThat(registration.waitTime().minutes()).isEqualTo(2);
		assertThat(snapshot.stage(QueueStage.PRIORITY_MEDIUM).orElseThrow().waitTime().minutes()).isZero();
	}

	@Test
	void keepsUnknownStagesSeparately() {
		respondWith("""
				{"t":1793900278,"expecting":2,"data":[
				  {"name":"Rejestracja","count":1,"wait":"5 min"},
				  {"name":"Rejestracja Ginekologia","count":1,"wait":"10 min"}]}
				""");

		QueueSnapshot snapshot = source.fetch(QUEUE_ID);

		assertThat(snapshot.stages()).containsOnlyKeys(QueueStage.REGISTRATION);
		assertThat(snapshot.otherStages()).extracting(QueueSnapshot.Stage::name).containsExactly("Rejestracja Ginekologia");
	}

	@Test
	void failsWhenSourceHasNoData() {
		respondWith("""
				{"t":1793900278,"data":null}
				""");

		assertThatThrownBy(() -> source.fetch(QUEUE_ID)).isInstanceOf(QueueNoDataException.class)
				.hasMessageContaining("Brak danych");
	}

	@Test
	void failsWhenGenerationTimeMissing() {
		respondWith("""
				{"data":[{"name":"Rejestracja","count":1,"wait":"5 min"}]}
				""");

		assertThatThrownBy(() -> source.fetch(QUEUE_ID)).isInstanceOf(QueueFetchException.class);
	}

	@Test
	void failsOnMalformedJson() {
		respondWith("<html>przerwa techniczna</html>");

		assertThatThrownBy(() -> source.fetch(QUEUE_ID)).isInstanceOf(QueueFetchException.class)
				.hasMessageContaining("Nieczytelna");
	}

	@Test
	void failsOnHttpError() {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://pacjent.gov.pl/api/v1/datatopsor/")))
				.andRespond(withServiceUnavailable());

		assertThatThrownBy(() -> source.fetch(QUEUE_ID)).isInstanceOf(QueueFetchException.class)
				.hasMessageContaining("503");
	}

	private void respondWith(String json) {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://pacjent.gov.pl/api/v1/datatopsor/")))
				.andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
	}

}
