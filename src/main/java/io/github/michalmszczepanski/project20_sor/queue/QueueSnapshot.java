package io.github.michalmszczepanski.project20_sor.queue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stan jednej kolejki SOR w chwili pomiaru.
 *
 * @param generatedAt     kiedy źródło wygenerowało dane (pole {@code t} odpowiedzi)
 * @param fetchedAt       kiedy o nie zapytaliśmy
 * @param patientsPresent wszyscy pacjenci obecni na SOR (pole {@code expecting})
 * @param stages          etapy znane z {@link QueueStage}
 * @param otherStages     etapy spoza listy (np. dodatkowa rejestracja), surowo — żeby nie ginęły
 */
public record QueueSnapshot(String queueId, Instant generatedAt, Instant fetchedAt, Integer patientsPresent,
		Map<QueueStage, Stage> stages, List<Stage> otherStages) {

	public QueueSnapshot {
		stages = Map.copyOf(stages);
		otherStages = List.copyOf(otherStages);
	}

	public Optional<Stage> stage(QueueStage stage) {
		return Optional.ofNullable(stages.get(stage));
	}

	public record Stage(String name, Integer patients, WaitTime waitTime) {
	}

}
