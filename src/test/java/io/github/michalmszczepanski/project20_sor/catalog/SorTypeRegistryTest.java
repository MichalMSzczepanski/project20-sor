package io.github.michalmszczepanski.project20_sor.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

class SorTypeRegistryTest {

	private static final Instant NOW = Instant.parse("2026-11-05T18:00:00Z");

	private Supplier<Map<String, SorType>> next;

	private final SorTypeRegistry registry = new SorTypeRegistry(() -> next.get(), Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void unknownUntilLoaded() {
		assertThat(registry.isLoaded()).isFalse();
		assertThat(registry.typeOf("000000007152-011")).isEqualTo(SorType.UNKNOWN);
	}

	@Test
	void returnsLoadedTypesAndUnknownForOthers() {
		next = () -> Map.of("000000007152-011", SorType.PEDIATRIC);

		registry.refresh();

		assertThat(registry.isLoaded()).isTrue();
		assertThat(registry.typeOf("000000007152-011")).isEqualTo(SorType.PEDIATRIC);
		assertThat(registry.typeOf("000000017654-631")).isEqualTo(SorType.UNKNOWN);
		assertThat(registry.status().loadedAt()).isEqualTo(NOW);
	}

	@Test
	void failedRefreshKeepsPreviousTypes() {
		next = () -> Map.of("000000007152-011", SorType.PEDIATRIC);
		registry.refresh();
		next = () -> {
			throw new IllegalStateException("RPWDL: HTTP 503");
		};

		registry.refresh();

		assertThat(registry.typeOf("000000007152-011")).isEqualTo(SorType.PEDIATRIC);
		assertThat(registry.status().lastError()).isEqualTo("RPWDL: HTTP 503");
	}

}
