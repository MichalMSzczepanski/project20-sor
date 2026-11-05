package io.github.michalmszczepanski.project20_sor.queue;

import java.time.Instant;
import java.util.Optional;

/**
 * Co wiemy o kolejce: ostatni poprawny pomiar i ostatni błąd (jeśli nowszy od pomiaru).
 *
 * @param latest      ostatni poprawny pomiar albo {@code null}
 * @param lastError   opis ostatniego błędu albo {@code null}
 * @param lastErrorAt kiedy wystąpił ostatni błąd albo {@code null}
 */
public record QueueState(QueueSnapshot latest, String lastError, Instant lastErrorAt) {

	public Optional<QueueSnapshot> latestSnapshot() {
		return Optional.ofNullable(latest);
	}

	QueueState withSnapshot(QueueSnapshot snapshot) {
		return new QueueState(snapshot, null, null);
	}

	QueueState withError(String error, Instant at) {
		return new QueueState(latest, error, at);
	}

}
