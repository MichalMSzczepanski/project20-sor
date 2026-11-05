package io.github.michalmszczepanski.project20_sor.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.michalmszczepanski.project20_sor.catalog.SorCatalog;
import io.github.michalmszczepanski.project20_sor.catalog.SorUnit;
import io.github.michalmszczepanski.project20_sor.config.SorProperties;
import io.github.michalmszczepanski.project20_sor.geo.GeoPoint;

class QueuePollerTest {

	private static final Instant NOW = Instant.parse("2026-11-05T18:00:00Z");

	private final QueueStateStore store = new QueueStateStore();

	private final Set<String> failing = ConcurrentHashMap.newKeySet();

	private final Map<String, Integer> calls = new ConcurrentHashMap<>();

	private List<SorUnit> units;

	private SorCatalog catalog;

	private QueuePoller poller;

	@BeforeEach
	void setUp() {
		// "a" występuje dwa razy — dwa SOR dzielące jedną kolejkę mają być odpytane raz
		units = List.of(unit("a"), unit("a"), unit("b"));
		catalog = new SorCatalog(() -> units);
		catalog.refresh();
		SorQueueSource source = id -> {
			calls.merge(id, 1, Integer::sum);
			if (failing.contains(id)) {
				throw new QueueFetchException("HTTP 503");
			}
			return new QueueSnapshot(id, NOW, NOW, 1, Map.of(), List.of());
		};
		SorProperties properties = new SorProperties(null, null,
				new SorProperties.Queue(Duration.ofMinutes(5), Duration.ZERO, 2, Duration.ofMinutes(15)), null, null);
		poller = new QueuePoller(catalog, source, store, Clock.fixed(NOW, ZoneOffset.UTC), properties);
	}

	@AfterEach
	void tearDown() {
		poller.destroy();
	}

	@Test
	void pollsEachQueueOnceAndRecordsResults() {
		failing.add("b");

		poller.pollAll();

		assertThat(calls).containsExactlyInAnyOrderEntriesOf(Map.of("a", 1, "b", 1));
		assertThat(store.find("a").orElseThrow().latestSnapshot()).isPresent();
		QueueState b = store.find("b").orElseThrow();
		assertThat(b.latestSnapshot()).isEmpty();
		assertThat(b.lastError()).isEqualTo("HTTP 503");
		assertThat(b.lastErrorAt()).isEqualTo(NOW);
	}

	@Test
	void successClearsPreviousError() {
		failing.add("a");
		poller.pollAll();
		failing.clear();

		poller.pollAll();

		QueueState a = store.find("a").orElseThrow();
		assertThat(a.lastError()).isNull();
		assertThat(a.latestSnapshot()).isPresent();
	}

	@Test
	void dropsQueuesRemovedFromCatalog() {
		poller.pollAll();
		units = List.of(unit("a"));
		catalog.refresh();

		poller.pollAll();

		assertThat(store.find("b")).isEmpty();
		assertThat(store.find("a")).isPresent();
	}

	private static SorUnit unit(String queueId) {
		return new SorUnit(queueId, "", "", new SorUnit.Address("", "", "", "", ""), "", "", "", new GeoPoint(52, 21));
	}

}
