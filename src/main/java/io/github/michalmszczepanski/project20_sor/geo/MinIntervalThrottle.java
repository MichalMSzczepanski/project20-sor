package io.github.michalmszczepanski.project20_sor.geo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Pilnuje minimalnego odstępu między zapytaniami do usługi z limitem (np. 1 zapytanie/s w Nominatim). */
class MinIntervalThrottle {

	@FunctionalInterface
	interface Sleeper {

		void sleep(Duration duration) throws InterruptedException;

	}

	private final Duration minInterval;

	private final Clock clock;

	private final Sleeper sleeper;

	private Instant last = Instant.MIN;

	MinIntervalThrottle(Duration minInterval, Clock clock, Sleeper sleeper) {
		this.minInterval = minInterval;
		this.clock = clock;
		this.sleeper = sleeper;
	}

	/** Czeka, aż minie odstęp od poprzedniego zapytania; zapytania przechodzą po kolei. */
	synchronized void acquire() throws InterruptedException {
		Instant now = clock.instant();
		if (!last.equals(Instant.MIN)) {
			Duration wait = Duration.between(now, last.plus(minInterval));
			if (!wait.isNegative() && !wait.isZero()) {
				sleeper.sleep(wait);
				now = clock.instant();
			}
		}
		last = now;
	}

}
