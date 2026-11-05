package io.github.michalmszczepanski.project20_sor.ranking;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.github.michalmszczepanski.project20_sor.catalog.SorCatalog;
import io.github.michalmszczepanski.project20_sor.catalog.SorTypeRegistry;
import io.github.michalmszczepanski.project20_sor.catalog.SorUnit;
import io.github.michalmszczepanski.project20_sor.config.SorProperties;
import io.github.michalmszczepanski.project20_sor.geo.GeoPoint;
import io.github.michalmszczepanski.project20_sor.geo.TravelEstimate;
import io.github.michalmszczepanski.project20_sor.geo.TravelEstimator;
import io.github.michalmszczepanski.project20_sor.queue.QueueState;
import io.github.michalmszczepanski.project20_sor.queue.QueueStateStore;

@Service
public class RankingService {

	private final SorCatalog catalog;

	private final SorTypeRegistry sorTypes;

	private final QueueStateStore queues;

	private final TravelEstimator travelEstimator;

	private final WaitEstimator waitEstimator;

	private final Clock clock;

	private final SorProperties.Ranking config;

	@Autowired
	public RankingService(SorCatalog catalog, SorTypeRegistry sorTypes, QueueStateStore queues,
			TravelEstimator travelEstimator, WaitEstimator waitEstimator, Clock clock, SorProperties properties) {
		this(catalog, sorTypes, queues, travelEstimator, waitEstimator, clock, properties.ranking());
	}

	RankingService(SorCatalog catalog, SorTypeRegistry sorTypes, QueueStateStore queues,
			TravelEstimator travelEstimator, WaitEstimator waitEstimator, Clock clock, SorProperties.Ranking config) {
		this.catalog = catalog;
		this.sorTypes = sorTypes;
		this.queues = queues;
		this.travelEstimator = travelEstimator;
		this.waitEstimator = waitEstimator;
		this.clock = clock;
		this.config = config;
	}

	/** @param priority priorytet z triażu albo {@code null}, gdy nieznany */
	public Recommendation recommend(GeoPoint origin, TriagePriority priority, PatientGroup patient, int limit) {
		Instant now = clock.instant();
		// Najpierw odrzucamy SOR, które pacjenta nie przyjmą, potem bierzemy najbliższe w linii prostej:
		// trasy liczymy tylko dla nich — dalsze i tak nie wygrają, a zapytanie zostaje małe.
		List<SorUnit> candidates = catalog.units()
				.stream()
				.filter(unit -> patient.accepts(sorTypes.typeOf(unit.queueId())))
				.sorted(Comparator.comparingDouble(u -> origin.distanceKmTo(u.location())))
				.limit(config.candidates())
				.toList();
		List<TravelEstimate> travel = travelEstimator.estimate(origin,
				candidates.stream().map(SorUnit::location).toList());
		List<SorRanking> all = IntStream.range(0, candidates.size())
				.mapToObj(i -> rank(candidates.get(i), travel.get(i), priority, now))
				.toList();

		List<SorRanking> ranked = all.stream()
				.filter(r -> r.waitEstimate().isUsable())
				.sorted(SorRanking.SHORTEST_TOTAL)
				.limit(limit)
				.toList();
		int horizon = ranked.stream().mapToInt(SorRanking::travelMinutes).max().orElse(Integer.MAX_VALUE);
		List<SorRanking> withoutData = all.stream()
				.filter(r -> !r.waitEstimate().isUsable() && r.travelMinutes() < horizon)
				.sorted(SorRanking.SHORTEST_TRAVEL)
				.limit(ranked.isEmpty() ? limit : config.maxWithoutData())
				.toList();
		return new Recommendation(ranked, withoutData, sorTypes.isLoaded());
	}

	private SorRanking rank(SorUnit unit, TravelEstimate travel, TriagePriority priority, Instant now) {
		Optional<QueueState> queue = queues.find(unit.queueId());
		WaitEstimate wait = waitEstimator.estimate(queue.flatMap(QueueState::latestSnapshot), priority, now);
		return new SorRanking(unit, sorTypes.typeOf(unit.queueId()), travel, wait, queue);
	}

}
