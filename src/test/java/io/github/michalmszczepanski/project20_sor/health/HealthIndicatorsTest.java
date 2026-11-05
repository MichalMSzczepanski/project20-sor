package io.github.michalmszczepanski.project20_sor.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import io.github.michalmszczepanski.project20_sor.catalog.SorCatalog;
import io.github.michalmszczepanski.project20_sor.catalog.SorCatalogData;
import io.github.michalmszczepanski.project20_sor.catalog.SorType;
import io.github.michalmszczepanski.project20_sor.catalog.SorTypeRegistry;
import io.github.michalmszczepanski.project20_sor.catalog.SorUnit;
import io.github.michalmszczepanski.project20_sor.config.SorProperties;
import io.github.michalmszczepanski.project20_sor.geo.GeoPoint;
import io.github.michalmszczepanski.project20_sor.queue.FreshnessPolicy;
import io.github.michalmszczepanski.project20_sor.queue.QueueFetchException;
import io.github.michalmszczepanski.project20_sor.queue.QueuePoller;
import io.github.michalmszczepanski.project20_sor.queue.QueueSnapshot;
import io.github.michalmszczepanski.project20_sor.queue.QueueStateStore;

class HealthIndicatorsTest {

	private static final Instant NOW = Instant.parse("2026-11-05T18:00:00Z");

	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

	private final QueueStateStore store = new QueueStateStore();

	private List<SorUnit> units = List.of();

	/** Czas wygenerowania pomiaru, który zwróci źródło dla danej kolejki; brak wpisu → błąd źródła. */
	private Map<String, Instant> generatedAt = Map.of();

	private SorCatalog catalog;

	private QueuePoller poller;

	private SorQueuesHealthIndicator queuesHealth;

	@BeforeEach
	void setUp() {
		catalog = new SorCatalog(() -> new SorCatalogData("/plik.json", units), CLOCK);
		poller = new QueuePoller(catalog, id -> {
			Instant at = generatedAt.get(id);
			if (at == null) {
				throw new QueueFetchException("HTTP 503");
			}
			return new QueueSnapshot(id, at, NOW, 1, Map.of(), List.of());
		}, store, CLOCK, new SorProperties(null, null, null,
				new SorProperties.Queue(Duration.ofMinutes(5), Duration.ZERO, 1, Duration.ofMinutes(15)), null, null,
				null));
		queuesHealth = new SorQueuesHealthIndicator(catalog, store, poller,
				new FreshnessPolicy(Duration.ofMinutes(15)), CLOCK, 0.5);
	}

	@AfterEach
	void tearDown() {
		poller.destroy();
	}

	@Test
	void catalogDownUntilLoaded() {
		Health health = new SorCatalogHealthIndicator(catalog).health();

		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
	}

	@Test
	void catalogUpWithSourceDetails() {
		units = List.of(unit("a"), unit("b"));
		catalog.refresh();

		Health health = new SorCatalogHealthIndicator(catalog).health();

		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).containsEntry("units", 2)
				.containsEntry("source", "/plik.json")
				.containsEntry("loadedAt", NOW)
				.doesNotContainKey("lastError");
	}

	@Test
	void sorTypesUnknownUntilLoadedThenUp() {
		SorTypeRegistry sorTypes = new SorTypeRegistry(() -> Map.of("a", SorType.PEDIATRIC), CLOCK);
		SorTypesHealthIndicator indicator = new SorTypesHealthIndicator(sorTypes);
		assertThat(indicator.health().getStatus()).isEqualTo(Status.UNKNOWN);

		sorTypes.refresh();

		assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
		assertThat(indicator.health().getDetails()).containsEntry("cells", 1).containsEntry("loadedAt", NOW);
	}

	@Test
	void queuesUnknownBeforeFirstPoll() {
		units = List.of(unit("a"));
		catalog.refresh();

		assertThat(queuesHealth.health().getStatus()).isEqualTo(Status.UNKNOWN);
	}

	@Test
	void queuesUpWhenEnoughFreshData() {
		units = List.of(unit("a"), unit("b"), unit("c"), unit("d"));
		catalog.refresh();
		generatedAt = Map.of("a", NOW, "b", NOW, "c", NOW.minus(Duration.ofMinutes(20)));
		poller.pollAll();

		Health health = queuesHealth.health();

		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).containsEntry("queues", 4)
				.containsEntry("fresh", 2)
				.containsEntry("stale", 1)
				.containsEntry("noData", 1)
				.containsEntry("withError", 1)
				.containsEntry("freshShare", 0.5)
				.containsEntry("lastPollFailures", 1);
	}

	@Test
	void queuesDownWhenSourceMostlyFails() {
		units = List.of(unit("a"), unit("b"), unit("c"));
		catalog.refresh();
		generatedAt = Map.of("a", NOW);
		poller.pollAll();

		Health health = queuesHealth.health();

		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).containsEntry("fresh", 1).containsEntry("withError", 2);
	}

	private static SorUnit unit(String queueId) {
		return new SorUnit(queueId, "", "", new SorUnit.Address("", "", "", "", ""), "", "", "", new GeoPoint(52, 21));
	}

}
