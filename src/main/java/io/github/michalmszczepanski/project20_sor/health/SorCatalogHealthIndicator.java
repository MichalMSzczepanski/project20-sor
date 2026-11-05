package io.github.michalmszczepanski.project20_sor.health;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;

import io.github.michalmszczepanski.project20_sor.catalog.SorCatalog;

/** Lista SOR: bez niej serwis nic nie pokaże. Nieudane odświeżenie przy wczytanej liście to tylko ostrzeżenie w szczegółach. */
@Component
class SorCatalogHealthIndicator extends AbstractHealthIndicator {

	private final SorCatalog catalog;

	SorCatalogHealthIndicator(SorCatalog catalog) {
		this.catalog = catalog;
	}

	@Override
	protected void doHealthCheck(Health.Builder builder) {
		SorCatalog.Status status = catalog.status();
		if (status.units() == 0) {
			builder.down().withDetail("reason", "lista SOR nie została wczytana");
		}
		else {
			builder.up()
					.withDetail("units", status.units())
					.withDetail("queues", status.queues())
					.withDetail("source", status.source())
					.withDetail("loadedAt", status.loadedAt());
		}
		if (status.lastError() != null) {
			builder.withDetail("lastError", status.lastError()).withDetail("lastErrorAt", status.lastErrorAt());
		}
	}

}
