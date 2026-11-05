package io.github.michalmszczepanski.project20_sor.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

class SchedulingConfigTest {

	private static final Instant NOW = Instant.parse("2026-11-05T18:00:00Z");

	private final AtomicBoolean loaded = new AtomicBoolean();

	private final Trigger trigger = SchedulingConfig.refreshTrigger(loaded::get, Duration.ofDays(7),
			Duration.ofMinutes(10));

	@Test
	void runsImmediatelyAtStartup() {
		assertThat(trigger.nextExecution(new SimpleTriggerContext(Clock.fixed(NOW, ZoneOffset.UTC)))).isEqualTo(NOW);
	}

	@Test
	void retriesSoonUntilLoadedThenRefreshesRarely() {
		SimpleTriggerContext afterRun = new SimpleTriggerContext(NOW, NOW, NOW);

		assertThat(trigger.nextExecution(afterRun)).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
		loaded.set(true);
		assertThat(trigger.nextExecution(afterRun)).isEqualTo(NOW.plus(Duration.ofDays(7)));
	}

}
