package io.github.michalmszczepanski.project20_sor.ranking;

import java.util.Comparator;
import java.util.Optional;

import io.github.michalmszczepanski.project20_sor.catalog.SorUnit;
import io.github.michalmszczepanski.project20_sor.geo.TravelEstimate;
import io.github.michalmszczepanski.project20_sor.queue.QueueState;

public record SorRanking(SorUnit unit, TravelEstimate travel, WaitEstimate waitEstimate, Optional<QueueState> queue) {

	/** Dla SOR ze świeżym szacunkiem: najkrótszy łączny czas dojazdu i oczekiwania. */
	static final Comparator<SorRanking> SHORTEST_TOTAL = Comparator
			.comparing((SorRanking r) -> r.totalMinutes().orElse(Integer.MAX_VALUE))
			.thenComparing(SorRanking::travelMinutes);

	/** Dla SOR bez wiarygodnych danych: najkrótszy dojazd. */
	static final Comparator<SorRanking> SHORTEST_TRAVEL = Comparator.comparing(SorRanking::travelMinutes)
			.thenComparingDouble(r -> r.travel().distanceKm());

	public Optional<Integer> totalMinutes() {
		return waitEstimate.isUsable() ? Optional.of(travel.minutes() + waitEstimate.minutes()) : Optional.empty();
	}

	int travelMinutes() {
		return travel.minutes();
	}

}
