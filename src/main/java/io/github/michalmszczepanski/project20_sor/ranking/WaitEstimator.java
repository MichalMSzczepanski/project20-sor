package io.github.michalmszczepanski.project20_sor.ranking;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import io.github.michalmszczepanski.project20_sor.queue.FreshnessPolicy;
import io.github.michalmszczepanski.project20_sor.queue.QueueSnapshot;
import io.github.michalmszczepanski.project20_sor.queue.QueueStage;
import io.github.michalmszczepanski.project20_sor.queue.WaitTime;

/**
 * Czas do lekarza = rejestracja + triaż + kolejka danego priorytetu (tak sumuje je pacjent.gov.pl).
 * Bez priorytetu porównujemy SOR według priorytetu średniego, a obok podajemy rozrzut dla priorytetów, które
 * zwykle dostają osoby przychodzące samodzielnie (średni–najniższy). Najgorszego przypadku nie bierzemy do porównań:
 * kolejki najniższego priorytetu w dużych miastach sięgają godzin i odsyłały ranking o kilkadziesiąt kilometrów.
 * To rozwiązanie tymczasowe — docelowo prognoza kolejki w chwili przyjazdu na podstawie historii.
 */
@Component
public class WaitEstimator {

	static final TriagePriority ASSUMED_WHEN_UNKNOWN = TriagePriority.MEDIUM;

	static final List<TriagePriority> RANGE_WHEN_UNKNOWN = List.of(TriagePriority.MEDIUM, TriagePriority.LOW,
			TriagePriority.LOWEST);

	private final FreshnessPolicy freshnessPolicy;

	public WaitEstimator(FreshnessPolicy freshnessPolicy) {
		this.freshnessPolicy = freshnessPolicy;
	}

	public WaitEstimate estimate(QueueSnapshot s, TriagePriority priority, Instant now) {
		WaitEstimate.Freshness freshness = freshnessPolicy.isFresh(s, now)
				? WaitEstimate.Freshness.FRESH
				: WaitEstimate.Freshness.STALE;
		Optional<Sum> basis = toDoctor(s, priority == null ? ASSUMED_WHEN_UNKNOWN : priority);
		if (basis.isEmpty()) {
			return new WaitEstimate(null, false, null, freshness, s.generatedAt());
		}
		WaitEstimate.Range range = priority == null ? range(s).orElse(null) : null;
		return new WaitEstimate(basis.get().minutes(), basis.get().atLeast(), range, freshness, s.generatedAt());
	}

	/** Rozrzut tylko wtedy, gdy znamy wszystkie przyjęte priorytety — inaczej byłby zaniżony. */
	private static Optional<WaitEstimate.Range> range(QueueSnapshot snapshot) {
		List<Sum> sums = new ArrayList<>(RANGE_WHEN_UNKNOWN.size());
		for (TriagePriority p : RANGE_WHEN_UNKNOWN) {
			Optional<Sum> sum = toDoctor(snapshot, p);
			if (sum.isEmpty()) {
				return Optional.empty();
			}
			sums.add(sum.get());
		}
		Sum longest = sums.stream().max(Comparator.comparingInt(Sum::minutes)).orElseThrow();
		int shortest = sums.stream().mapToInt(Sum::minutes).min().orElseThrow();
		return Optional.of(new WaitEstimate.Range(shortest, longest.minutes(), longest.atLeast()));
	}

	private static Optional<Sum> toDoctor(QueueSnapshot snapshot, TriagePriority priority) {
		int total = 0;
		boolean atLeast = false;
		for (QueueStage stage : List.of(QueueStage.REGISTRATION, QueueStage.TRIAGE, priority.stage())) {
			Optional<WaitTime> wait = snapshot.stage(stage).map(QueueSnapshot.Stage::waitTime).filter(WaitTime::isKnown);
			if (wait.isEmpty()) {
				return Optional.empty();
			}
			total += wait.get().minutes();
			atLeast |= wait.get().atLeast();
		}
		return Optional.of(new Sum(total, atLeast));
	}

	private record Sum(int minutes, boolean atLeast) {
	}

}
