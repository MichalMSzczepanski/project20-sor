package io.github.michalmszczepanski.project20_sor.queue;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kolejki z systemu TOPSOR przez API pacjent.gov.pl.
 * Parametr {@code t} jest obowiązkowy — bez niego portal oddaje starą kopię z własnego cache.
 */
@Component
class TopsorQueueSource implements SorQueueSource {

	private final RestClient restClient;

	private final JsonMapper jsonMapper;

	private final Clock clock;

	TopsorQueueSource(RestClient pacjentGovRestClient, JsonMapper jsonMapper, Clock clock) {
		this.restClient = pacjentGovRestClient;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	@Override
	public QueueSnapshot fetch(String queueId) {
		Instant fetchedAt = clock.instant();
		TopsorResponse response = request(queueId, fetchedAt);
		if (response.t() == null) {
			throw new QueueFetchException("Brak czasu wygenerowania (t) dla " + queueId);
		}
		if (response.data() == null || response.data().isEmpty()) {
			throw new QueueFetchException("Brak danych kolejki dla " + queueId);
		}
		Map<QueueStage, QueueSnapshot.Stage> stages = new EnumMap<>(QueueStage.class);
		List<QueueSnapshot.Stage> otherStages = new ArrayList<>();
		for (TopsorResponse.Entry entry : response.data()) {
			if (entry == null || entry.name() == null) {
				throw new QueueFetchException("Etap bez nazwy w odpowiedzi dla " + queueId);
			}
			QueueSnapshot.Stage stage = new QueueSnapshot.Stage(entry.name().trim(), entry.count(),
					WaitTimeParser.parse(entry.waitText()));
			Optional<QueueStage> known = QueueStage.fromSourceName(entry.name());
			if (known.isPresent() && !stages.containsKey(known.get())) {
				stages.put(known.get(), stage);
			}
			else {
				otherStages.add(stage);
			}
		}
		return new QueueSnapshot(queueId, Instant.ofEpochSecond(response.t()), fetchedAt, response.expecting(), stages,
				otherStages);
	}

	private TopsorResponse request(String queueId, Instant now) {
		byte[] body;
		try {
			body = restClient.get()
					.uri("/api/v1/datatopsor/{id}?_format=json&t={t}", queueId, now.getEpochSecond())
					.retrieve()
					.body(byte[].class);
		}
		catch (RestClientException ex) {
			throw new QueueFetchException("Błąd zapytania o kolejkę " + queueId + ": " + ex.getMessage(), ex);
		}
		if (body == null) {
			throw new QueueFetchException("Pusta odpowiedź dla " + queueId);
		}
		try {
			return jsonMapper.readValue(body, TopsorResponse.class);
		}
		catch (JacksonException ex) {
			throw new QueueFetchException("Nieczytelna odpowiedź dla " + queueId, ex);
		}
	}

}
