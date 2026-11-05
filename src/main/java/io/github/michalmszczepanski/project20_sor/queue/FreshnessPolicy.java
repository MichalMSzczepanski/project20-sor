package io.github.michalmszczepanski.project20_sor.queue;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.michalmszczepanski.project20_sor.config.SorProperties;

/** Jedna reguła, kiedy pomiar kolejki jest za stary, by na nim polegać — dla rankingu i dla monitoringu. */
@Component
public class FreshnessPolicy {

	private final Duration staleAfter;

	@Autowired
	public FreshnessPolicy(SorProperties properties) {
		this(properties.queue().staleAfter());
	}

	public FreshnessPolicy(Duration staleAfter) {
		this.staleAfter = staleAfter;
	}

	public boolean isFresh(QueueSnapshot snapshot, Instant now) {
		return Duration.between(snapshot.generatedAt(), now).compareTo(staleAfter) <= 0;
	}

}
