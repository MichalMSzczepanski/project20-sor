package io.github.michalmszczepanski.project20_sor.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class SorPropertiesTest {

	@Test
	void pollWithRetryMustFitInInterval() {
		assertThatCode(() -> new SorProperties.Queue(Duration.ofMinutes(5), Duration.ZERO, Duration.ofMinutes(4),
				Duration.ofSeconds(30), Duration.ofMinutes(15), 4)).doesNotThrowAnyException();
		assertThatThrownBy(() -> new SorProperties.Queue(Duration.ofMinutes(5), Duration.ZERO, Duration.ofMinutes(5),
				Duration.ofSeconds(30), Duration.ofMinutes(15), 4)).isInstanceOf(IllegalArgumentException.class);
	}

}
