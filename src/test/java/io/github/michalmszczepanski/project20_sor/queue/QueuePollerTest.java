package io.github.michalmszczepanski.project20_sor.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.michalmszczepanski.project20_sor.catalog.SorCatalog;
import io.github.michalmszczepanski.project20_sor.catalog.SorCatalogData;
import io.github.michalmszczepanski.project20_sor.catalog.SorUnit;
import io.github.michalmszczepanski.project20_sor.geo.GeoPoint;
import io.github.michalmszczepanski.project20_sor.support.ManualClock;

class QueuePollerTest {

	private static final Instant START = Instant.parse("2026-11-05T18:00:00Z");

	private final ManualClock clock = new ManualClock(START);

	private final QueueStateStore store = new QueueStateStore();

	/** Kolejki, dla których źródło zwraca „brak danych” — tyle razy, ile podano. */
	private final Map<String, Integer> noDataTimes = new ConcurrentHashMap<>();

	private final Set<String> httpErrors = ConcurrentHashMap.newKeySet();

	// Pierwszy przebieg idzie współbieżnie — listy muszą to znieść.
	private final List<String> calls = Collections.synchronizedList(new ArrayList<>());

	private final List<Instant> callTimes = Collections.synchronizedList(new ArrayList<>());

	private final List<Duration> sleeps = Collections.synchronizedList(new ArrayList<>());

	private List<SorUnit> units;

	private SorCatalog catalog;

	private QueuePoller poller;

	@BeforeEach
	void setUp() {
		// "a" występuje dwa razy — dwa SOR dzielące jedną kolejkę mają być odpytane raz
		units = List.of(unit("a"), unit("a"), unit("b"), unit("c"), unit("d"));
		catalog = new SorCatalog(() -> new SorCatalogData("test", units), clock);
		catalog.refresh();
		SorQueueSource source = id -> {
			calls.add(id);
			callTimes.add(clock.instant());
			clock.advance(Duration.ofMillis(200)); // samo zapytanie trwa
			if (noDataTimes.getOrDefault(id, 0) > 0) {
				noDataTimes.merge(id, -1, Integer::sum);
				throw new QueueNoDataException("Brak danych kolejki dla " + id);
			}
			if (httpErrors.contains(id)) {
				throw new QueueFetchException("HTTP 503");
			}
			return new QueueSnapshot(id, clock.instant(), clock.instant(), 1, Map.of(), List.of());
		};
		poller = new QueuePoller(catalog, source, store, clock, duration -> {
			sleeps.add(duration);
			clock.advance(duration);
		}, Duration.ofMinutes(4), Duration.ofSeconds(30), 2);
	}

	/** Pierwszy przebieg po starcie ma osobny tryb; testy przebiegu równomiernego zaczynają od drugiego. */
	private void afterStartupPoll() {
		poller.pollAll();
		calls.clear();
		callTimes.clear();
		sleeps.clear();
	}

	@Test
	void firstPollAfterStartupIsConcurrentAndQuick() {
		poller.pollAll();

		assertThat(calls).containsExactlyInAnyOrder("a", "b", "c", "d");
		assertThat(sleeps).isEmpty();
		// 4 zapytania po 200 ms, 2 naraz — bez czekania na sloty co minutę
		assertThat(poller.lastPoll().orElseThrow().took()).isLessThan(Duration.ofSeconds(5));
	}

	@Test
	void firstPollAlsoRetriesQueuesWithoutData() {
		noDataTimes.put("c", 1);

		poller.pollAll();

		assertThat(calls).hasSize(5).containsOnly("a", "b", "c", "d");
		assertThat(sleeps).containsExactly(Duration.ofSeconds(30));
		assertThat(store.find("c").orElseThrow().lastError()).isNull();
	}

	@Test
	void pollsEachQueueOnceInEvenlySpacedSlots() {
		afterStartupPoll();
		Instant start = clock.instant();

		poller.pollAll();

		assertThat(calls).containsExactly("a", "b", "c", "d");
		// 4 kolejki w 4 minuty: start co minutę, niezależnie od czasu trwania zapytania
		assertThat(callTimes).containsExactly(start, start.plusSeconds(60), start.plusSeconds(120),
				start.plusSeconds(180));
	}

	@Test
	void retriesQueuesWithoutDataOnceAfterDelay() {
		afterStartupPoll();
		noDataTimes.put("b", 1);
		noDataTimes.put("d", 5);

		poller.pollAll();

		assertThat(calls).containsExactly("a", "b", "c", "d", "b", "d");
		assertThat(sleeps).contains(Duration.ofSeconds(30));
		assertThat(store.find("b").orElseThrow().lastError()).isNull();
		assertThat(store.find("b").orElseThrow().latestSnapshot()).isPresent();
		assertThat(store.find("d").orElseThrow().lastError()).contains("Brak danych");
		assertThat(poller.lastPoll().orElseThrow().failures()).isEqualTo(1);
	}

	@Test
	void retriesUseSameGapSoCycleFitsInterval() {
		afterStartupPoll();
		noDataTimes.put("a", 1);
		noDataTimes.put("b", 1);
		int retryStart = 4;

		poller.pollAll();

		// Ponowienia co minutę (jak w głównym przebiegu dla 4 kolejek), a nie rozciągnięte na całe 4 minuty.
		assertThat(Duration.between(callTimes.get(retryStart), callTimes.get(retryStart + 1)))
				.isEqualTo(Duration.ofSeconds(60));
		assertThat(poller.lastPoll().orElseThrow().took()).isLessThan(Duration.ofMinutes(5));
	}

	@Test
	void doesNotRetryOtherErrors() {
		afterStartupPoll();
		httpErrors.add("c");

		poller.pollAll();

		assertThat(calls).containsExactly("a", "b", "c", "d");
		assertThat(sleeps).doesNotContain(Duration.ofSeconds(30));
		assertThat(store.find("c").orElseThrow().lastError()).isEqualTo("HTTP 503");
	}

	@Test
	void recordsSummaryOfLastPoll() {
		afterStartupPoll();
		httpErrors.add("b");

		poller.pollAll();

		QueuePoller.PollSummary summary = poller.lastPoll().orElseThrow();
		assertThat(summary.queues()).isEqualTo(4);
		assertThat(summary.failures()).isEqualTo(1);
		assertThat(summary.took()).isBetween(Duration.ofMinutes(3), Duration.ofMinutes(4));
	}

	@Test
	void successClearsPreviousError() {
		httpErrors.add("a");
		poller.pollAll();
		httpErrors.clear();

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

	@Test
	void stopsWhenInterrupted() {
		QueuePoller interrupted = new QueuePoller(catalog, id -> {
			calls.add(id);
			return new QueueSnapshot(id, START, START, 1, Map.of(), List.of());
		}, store, clock, duration -> {
			throw new InterruptedException();
		}, Duration.ofMinutes(4), Duration.ofSeconds(30), 2);

		interrupted.pollAll(); // pierwszy przebieg: współbieżny, bez czekania
		calls.clear();
		interrupted.pollAll();

		assertThat(calls).containsExactly("a");
		assertThat(Thread.interrupted()).isTrue(); // flaga przywrócona; czyścimy ją dla kolejnych testów
	}

	private static SorUnit unit(String queueId) {
		return new SorUnit(queueId, "", "", new SorUnit.Address("", "", "", "", ""), "", "", "", new GeoPoint(52, 21));
	}

}
