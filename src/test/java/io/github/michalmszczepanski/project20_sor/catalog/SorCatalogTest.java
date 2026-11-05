package io.github.michalmszczepanski.project20_sor.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import io.github.michalmszczepanski.project20_sor.geo.GeoPoint;

class SorCatalogTest {

	private static final Instant NOW = Instant.parse("2026-11-05T18:00:00Z");

	private Supplier<SorCatalogData> next;

	private final SorCatalog catalog = new SorCatalog(() -> next.get(), Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void reportsWhereAndWhenListWasLoaded() {
		next = () -> new SorCatalogData("/plik-1.json", List.of(unit("a"), unit("a"), unit("b")));

		catalog.refresh();

		assertThat(catalog.status()).isEqualTo(new SorCatalog.Status(3, 2, "/plik-1.json", NOW, null, null));
		assertThat(catalog.queueIds()).containsExactlyInAnyOrder("a", "b");
	}

	@Test
	void failedRefreshKeepsPreviousListAndReportsError() {
		next = () -> new SorCatalogData("/plik-1.json", List.of(unit("a")));
		catalog.refresh();
		next = () -> {
			throw new IllegalStateException("HTTP 503");
		};

		catalog.refresh();

		SorCatalog.Status status = catalog.status();
		assertThat(status.units()).isEqualTo(1);
		assertThat(status.source()).isEqualTo("/plik-1.json");
		assertThat(status.lastError()).isEqualTo("HTTP 503");
		assertThat(status.lastErrorAt()).isEqualTo(NOW);
	}

	@Test
	void emptyListCountsAsFailure() {
		next = () -> new SorCatalogData("/pusty.json", List.of());

		catalog.refresh();

		assertThat(catalog.units()).isEmpty();
		assertThat(catalog.status().lastError()).contains("pustą listę");
	}

	@Test
	void successfulRefreshClearsError() {
		next = () -> {
			throw new IllegalStateException("HTTP 503");
		};
		catalog.refresh();
		next = () -> new SorCatalogData("/plik-2.json", List.of(unit("a")));

		catalog.refresh();

		assertThat(catalog.status().lastError()).isNull();
		assertThat(catalog.status().source()).isEqualTo("/plik-2.json");
	}

	private static SorUnit unit(String queueId) {
		return new SorUnit(queueId, "", "", new SorUnit.Address("", "", "", "", ""), "", "", "", new GeoPoint(52, 21));
	}

}
