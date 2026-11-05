package io.github.michalmszczepanski.project20_sor.catalog;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ostatnie udane wczytanie rodzajów SOR zapisane na dysku — tymczasowo, do czasu właściwego cache.
 * Pozwala po restarcie od razu odróżniać SOR dla dzieci, nawet gdy RPWDL odpowiada bardzo wolno.
 */
public class SorTypeSnapshotFile implements SorTypeSnapshotStore {

	private static final Logger log = LoggerFactory.getLogger(SorTypeSnapshotFile.class);

	private final Path file;

	private final JsonMapper jsonMapper;

	public SorTypeSnapshotFile(Path file, JsonMapper jsonMapper) {
		this.file = file;
		this.jsonMapper = jsonMapper;
	}

	@Override
	public Optional<Snapshot> read() {
		if (!Files.isRegularFile(file)) {
			return Optional.empty();
		}
		try {
			Snapshot snapshot = jsonMapper.readValue(file.toFile(), Snapshot.class);
			return snapshot.types() == null || snapshot.savedAt() == null || snapshot.types().isEmpty()
					? Optional.empty()
					: Optional.of(snapshot);
		}
		catch (JacksonException ex) {
			log.warn("Nieczytelny plik z rodzajami SOR {} — pomijam: {}", file, ex.getOriginalMessage());
			return Optional.empty();
		}
	}

	/** Zapis przez plik tymczasowy i podmianę — przerwany zapis nie zostawi uszkodzonego pliku. */
	@Override
	public void write(Map<String, SorType> types, Instant savedAt) {
		try {
			Path dir = file.toAbsolutePath().getParent();
			Files.createDirectories(dir);
			Path temp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
			jsonMapper.writeValue(temp.toFile(), new Snapshot(savedAt, types));
			try {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (AtomicMoveNotSupportedException ex) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException | JacksonException ex) {
			log.warn("Nie udało się zapisać rodzajów SOR do {}: {}", file, ex.getMessage());
		}
	}

}
