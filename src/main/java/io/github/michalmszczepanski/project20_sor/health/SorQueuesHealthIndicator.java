package io.github.michalmszczepanski.project20_sor.health;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;

import io.github.michalmszczepanski.project20_sor.catalog.SorCatalog;
import io.github.michalmszczepanski.project20_sor.config.SorProperties;
import io.github.michalmszczepanski.project20_sor.queue.FreshnessPolicy;
import io.github.michalmszczepanski.project20_sor.queue.QueuePoller;
import io.github.michalmszczepanski.project20_sor.queue.QueueSnapshot;
import io.github.michalmszczepanski.project20_sor.queue.QueueState;
import io.github.michalmszczepanski.project20_sor.queue.QueueStateStore;

/**
 * Kolejki SOR: ile ma świeże dane. Za mało świeżych (np. pacjent.gov.pl leży) → DOWN, bo ranking traci sens.
 * Przed pierwszym przebiegiem odpytywania stan jest nieznany.
 */
@Component
class SorQueuesHealthIndicator extends AbstractHealthIndicator {

	private final SorCatalog catalog;

	private final QueueStateStore store;

	private final QueuePoller poller;

	private final FreshnessPolicy freshnessPolicy;

	private final Clock clock;

	private final double minFreshShare;

	@Autowired
	SorQueuesHealthIndicator(SorCatalog catalog, QueueStateStore store, QueuePoller poller,
			FreshnessPolicy freshnessPolicy, Clock clock, SorProperties properties) {
		this(catalog, store, poller, freshnessPolicy, clock, properties.health().minFreshShare());
	}

	SorQueuesHealthIndicator(SorCatalog catalog, QueueStateStore store, QueuePoller poller,
			FreshnessPolicy freshnessPolicy, Clock clock, double minFreshShare) {
		this.catalog = catalog;
		this.store = store;
		this.poller = poller;
		this.freshnessPolicy = freshnessPolicy;
		this.clock = clock;
		this.minFreshShare = minFreshShare;
	}

	@Override
	protected void doHealthCheck(Health.Builder builder) {
		Set<String> queueIds = catalog.queueIds();
		Optional<QueuePoller.PollSummary> lastPoll = poller.lastPoll();
		if (queueIds.isEmpty() || lastPoll.isEmpty()) {
			builder.unknown().withDetail("reason", queueIds.isEmpty() ? "brak listy SOR" : "kolejki nie były jeszcze odpytane");
			return;
		}
		Instant now = clock.instant();
		int fresh = 0;
		int stale = 0;
		int noData = 0;
		int withError = 0;
		for (String queueId : queueIds) {
			Optional<QueueState> state = store.find(queueId);
			Optional<QueueSnapshot> snapshot = state.flatMap(QueueState::latestSnapshot);
			if (snapshot.isEmpty()) {
				noData++;
			}
			else if (freshnessPolicy.isFresh(snapshot.get(), now)) {
				fresh++;
			}
			else {
				stale++;
			}
			if (state.map(QueueState::lastError).isPresent()) {
				withError++;
			}
		}
		double freshShare = (double) fresh / queueIds.size();
		(freshShare >= minFreshShare ? builder.up() : builder.down())
				.withDetail("queues", queueIds.size())
				.withDetail("fresh", fresh)
				.withDetail("stale", stale)
				.withDetail("noData", noData)
				.withDetail("withError", withError)
				.withDetail("freshShare", Math.round(freshShare * 100) / 100.0)
				.withDetail("minFreshShare", minFreshShare)
				.withDetail("lastPollFinishedAt", lastPoll.get().finishedAt())
				.withDetail("lastPollSeconds", lastPoll.get().took().toSeconds())
				.withDetail("lastPollFailures", lastPoll.get().failures());
	}

}
