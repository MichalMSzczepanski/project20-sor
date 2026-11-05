package io.github.michalmszczepanski.project20_sor.support;

import java.time.Clock;
import java.time.Instant;

/**
 * Wartość wczytywana z zewnętrznego źródła: nieudane odświeżenie zostawia poprzednią
 * i zapamiętuje błąd — do monitoringu i do decyzji, czy ponawiać szybciej.
 */
public final class RefreshableValue<T> {

	private final Clock clock;

	private volatile Loaded<T> loaded;

	private volatile Failure failure;

	public RefreshableValue(T initial, Clock clock) {
		this.clock = clock;
		this.loaded = new Loaded<>(initial, null);
	}

	public void set(T value) {
		set(value, clock.instant());
	}

	/** Wartość wczytana wcześniej (np. z pliku) — z jej prawdziwym czasem wczytania. */
	public void set(T value, Instant loadedAt) {
		loaded = new Loaded<>(value, loadedAt);
		failure = null;
	}

	public void fail(String reason) {
		failure = new Failure(reason, clock.instant());
	}

	public T value() {
		return loaded.value();
	}

	/** Czy choć raz udało się wczytać wartość. */
	public boolean isLoaded() {
		return loaded.at() != null;
	}

	public Status status() {
		Loaded<T> current = loaded;
		Failure lastFailure = failure;
		return new Status(current.at(), lastFailure == null ? null : lastFailure.reason(),
				lastFailure == null ? null : lastFailure.at());
	}

	/**
	 * @param loadedAt    kiedy wczytano obecną wartość; {@code null}, gdy jeszcze nigdy
	 * @param lastError   błąd ostatniego odświeżenia, jeśli było nieudane
	 * @param lastErrorAt kiedy wystąpił
	 */
	public record Status(Instant loadedAt, String lastError, Instant lastErrorAt) {
	}

	private record Loaded<T>(T value, Instant at) {
	}

	private record Failure(String reason, Instant at) {
	}

}
