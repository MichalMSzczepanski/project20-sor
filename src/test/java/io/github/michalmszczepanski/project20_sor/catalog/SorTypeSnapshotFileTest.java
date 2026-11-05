package io.github.michalmszczepanski.project20_sor.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.json.JsonMapper;

class SorTypeSnapshotFileTest {

	private static final Instant SAVED = Instant.parse("2026-11-05T20:00:00Z");

	private static final Map<String, SorType> TYPES = Map.of("000000007152-011", SorType.PEDIATRIC,
			"000000025802-079", SorType.GENERAL);

	@TempDir
	Path dir;

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Test
	void writesAndReadsBackIncludingMissingDirectories() {
		SorTypeSnapshotFile file = new SorTypeSnapshotFile(dir.resolve("data/sor-types.json"), jsonMapper);

		file.write(TYPES, SAVED);

		assertThat(file.read()).contains(new SorTypeSnapshotStore.Snapshot(SAVED, TYPES));
		try (var files = Files.list(dir.resolve("data"))) {
			assertThat(files).extracting(p -> p.getFileName().toString()).containsExactly("sor-types.json");
		}
		catch (IOException ex) {
			throw new AssertionError(ex);
		}
	}

	@Test
	void missingCorruptOrEmptyFileGivesNothing() throws IOException {
		assertThat(new SorTypeSnapshotFile(dir.resolve("brak.json"), jsonMapper).read()).isEmpty();

		Path corrupt = Files.writeString(dir.resolve("zly.json"), "{\"savedAt\":");
		assertThat(new SorTypeSnapshotFile(corrupt, jsonMapper).read()).isEmpty();

		Path empty = Files.writeString(dir.resolve("pusty.json"), "{\"savedAt\":\"2026-11-05T20:00:00Z\",\"types\":{}}");
		assertThat(new SorTypeSnapshotFile(empty, jsonMapper).read()).isEmpty();
	}

	@Test
	void registryRestoresFromFileAndSavesAfterRefresh() {
		SorTypeSnapshotFile file = new SorTypeSnapshotFile(dir.resolve("sor-types.json"), jsonMapper);
		file.write(TYPES, SAVED);
		Instant now = Instant.parse("2026-11-05T21:00:00Z");
		SorTypeRegistry registry = new SorTypeRegistry(() -> Map.of("000000001137-014", SorType.GENERAL),
				Clock.fixed(now, ZoneOffset.UTC), file);

		registry.afterPropertiesSet();

		assertThat(registry.isLoaded()).isTrue();
		assertThat(registry.typeOf("000000007152-011")).isEqualTo(SorType.PEDIATRIC);
		assertThat(registry.status().loadedAt()).isEqualTo(SAVED);

		registry.refresh();

		assertThat(registry.typeOf("000000007152-011")).isEqualTo(SorType.UNKNOWN);
		assertThat(file.read()).contains(new SorTypeSnapshotStore.Snapshot(now, Map.of("000000001137-014", SorType.GENERAL)));
	}

	@Test
	void failedRefreshKeepsFileUntouched() {
		SorTypeSnapshotFile file = new SorTypeSnapshotFile(dir.resolve("sor-types.json"), jsonMapper);
		file.write(TYPES, SAVED);
		SorTypeRegistry registry = new SorTypeRegistry(() -> {
			throw new IllegalStateException("RPWDL: limit czasu");
		}, Clock.systemUTC(), file);
		registry.afterPropertiesSet();

		registry.refresh();

		assertThat(file.read()).contains(new SorTypeSnapshotStore.Snapshot(SAVED, TYPES));
		assertThat(registry.typeOf("000000007152-011")).isEqualTo(SorType.PEDIATRIC);
	}

}
