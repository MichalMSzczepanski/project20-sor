package io.github.michalmszczepanski.project20_sor.ranking;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.michalmszczepanski.project20_sor.config.SorProperties;
import io.github.michalmszczepanski.project20_sor.queue.QueueSnapshot;
import io.github.michalmszczepanski.project20_sor.queue.QueueStage;
import io.github.michalmszczepanski.project20_sor.queue.WaitTime;

/**
 * Czas do lekarza = rejestracja + triaż + kolejka danego priorytetu (tak sumuje je pacjent.gov.pl).
 * Bez priorytetu liczymy tylko etapy, przez które przechodzi każdy pacjent.
 */
@Component
public class WaitEstimator {

	private final Duration staleAfter;

	@Autowired
	public WaitEstimator(SorProperties properties) {
		this(properties.queue().staleAfter());
	}

	WaitEstimator(Duration staleAfter) {
		this.staleAfter = staleAfter;
	}

	public WaitEstimate estimate(Optional<QueueSnapshot> snapshot, TriagePriority priority, Instant now) {
		if (snapshot.isEmpty()) {
			return WaitEstimate.NO_DATA;
		}
		QueueSnapshot s = snapshot.get();
		WaitEstimate.Freshness freshness = Duration.between(s.generatedAt(), now).compareTo(staleAfter) > 0
				? WaitEstimate.Freshness.STALE
				: WaitEstimate.Freshness.FRESH;

		List<QueueStage> stages = new ArrayList<>(List.of(QueueStage.REGISTRATION, QueueStage.TRIAGE));
		if (priority != null) {
			stages.add(priority.stage());
		}
		int total = 0;
		boolean atLeast = false;
		for (QueueStage stage : stages) {
			Optional<WaitTime> wait = s.stage(stage).map(QueueSnapshot.Stage::waitTime).filter(WaitTime::isKnown);
			if (wait.isEmpty()) {
				return new WaitEstimate(null, false, freshness, s.generatedAt());
			}
			total += wait.get().minutes();
			atLeast |= wait.get().atLeast();
		}
		return new WaitEstimate(total, atLeast, freshness, s.generatedAt());
	}

}
