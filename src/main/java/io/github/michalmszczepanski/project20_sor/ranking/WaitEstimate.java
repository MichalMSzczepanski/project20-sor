package io.github.michalmszczepanski.project20_sor.ranking;

import java.time.Instant;

/**
 * Szacowany czas do kontaktu z lekarzem na SOR.
 *
 * @param minutes     suma etapów; {@code null}, gdy któregoś etapu nie znamy
 * @param atLeast     któryś etap podano tylko jako dolną granicę
 * @param freshness   czy pomiar nadaje się do porównań
 * @param generatedAt kiedy źródło wygenerowało pomiar; {@code null} przy braku danych
 */
public record WaitEstimate(Integer minutes, boolean atLeast, Freshness freshness, Instant generatedAt) {

	public enum Freshness {
		FRESH, STALE, NO_DATA
	}

	static final WaitEstimate NO_DATA = new WaitEstimate(null, false, Freshness.NO_DATA, null);

	/** Czy można tym szacunkiem uszeregować SOR. */
	public boolean isUsable() {
		return freshness == Freshness.FRESH && minutes != null;
	}

}
