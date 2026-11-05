package io.github.michalmszczepanski.project20_sor.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.michalmszczepanski.project20_sor.support.ManualClock;

class MinIntervalThrottleTest {

	private final ManualClock clock = new ManualClock(Instant.parse("2026-11-05T18:00:00Z"));

	private final List<Duration> sleeps = new ArrayList<>();

	private final MinIntervalThrottle throttle = new MinIntervalThrottle(Duration.ofSeconds(1), clock, duration -> {
		sleeps.add(duration);
		clock.advance(duration);
	});

	@Test
	void firstRequestDoesNotWait() throws InterruptedException {
		throttle.acquire();

		assertThat(sleeps).isEmpty();
	}

	@Test
	void waitsForRestOfIntervalBetweenRequests() throws InterruptedException {
		throttle.acquire();
		clock.advance(Duration.ofMillis(300));

		throttle.acquire();
		throttle.acquire();

		assertThat(sleeps).containsExactly(Duration.ofMillis(700), Duration.ofSeconds(1));
	}

	@Test
	void doesNotWaitWhenIntervalAlreadyPassed() throws InterruptedException {
		throttle.acquire();
		clock.advance(Duration.ofSeconds(5));

		throttle.acquire();

		assertThat(sleeps).isEmpty();
	}

}
