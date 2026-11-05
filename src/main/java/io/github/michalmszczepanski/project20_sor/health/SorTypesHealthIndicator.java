package io.github.michalmszczepanski.project20_sor.health;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;

import io.github.michalmszczepanski.project20_sor.catalog.SorTypeRegistry;
import io.github.michalmszczepanski.project20_sor.support.RefreshableValue;

/**
 * Rodzaje SOR z RPWDL. Bez nich serwis działa, ale nie odróżnia SOR dla dzieci — stąd UNKNOWN, a nie DOWN;
 * interfejs ostrzega użytkownika osobno.
 */
@Component
class SorTypesHealthIndicator extends AbstractHealthIndicator {

	private final SorTypeRegistry sorTypes;

	SorTypesHealthIndicator(SorTypeRegistry sorTypes) {
		this.sorTypes = sorTypes;
	}

	@Override
	protected void doHealthCheck(Health.Builder builder) {
		RefreshableValue.Status status = sorTypes.status();
		if (sorTypes.isLoaded()) {
			builder.up().withDetail("cells", sorTypes.size()).withDetail("loadedAt", status.loadedAt());
		}
		else {
			builder.unknown().withDetail("reason", "rodzaje SOR z RPWDL nie zostały jeszcze wczytane");
		}
		if (status.lastError() != null) {
			builder.withDetail("lastError", status.lastError()).withDetail("lastErrorAt", status.lastErrorAt());
		}
	}

}
