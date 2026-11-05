package io.github.michalmszczepanski.project20_sor.catalog;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** Miejsce na ostatni udany wynik rodzajów SOR; {@link #NONE} — bez zapisu (np. w testach). */
public interface SorTypeSnapshotStore {

	SorTypeSnapshotStore NONE = new SorTypeSnapshotStore() {

		@Override
		public Optional<Snapshot> read() {
			return Optional.empty();
		}

		@Override
		public void write(Map<String, SorType> types, Instant savedAt) {
			// celowo nic
		}

	};

	Optional<Snapshot> read();

	void write(Map<String, SorType> types, Instant savedAt);

	record Snapshot(Instant savedAt, Map<String, SorType> types) {
	}

}
