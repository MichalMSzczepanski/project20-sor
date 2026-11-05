package io.github.michalmszczepanski.project20_sor.ranking;

import java.time.Instant;

/**
 * Szacowany czas do kontaktu z lekarzem na SOR.
 *
 * @param minutes     czas do porównań; {@code null}, gdy któregoś potrzebnego etapu nie znamy
 * @param atLeast     któryś etap podano tylko jako dolną granicę („powyżej 2 godzin”)
 * @param range       rozrzut zależny od priorytetu, gdy priorytet z triażu jest nieznany; inaczej {@code null}
 * @param freshness   czy pomiar nadaje się do porównań
 * @param generatedAt kiedy źródło wygenerowało pomiar; {@code null} przy braku danych
 */
public record WaitEstimate(Integer minutes, boolean atLeast, Range range, Freshness freshness, Instant generatedAt) {

	public enum Freshness {
		FRESH, STALE, NO_DATA
	}

	/** Od najkrótszego do najdłuższego czasu wśród priorytetów przyjętych przy nieznanym priorytecie. */
	public record Range(int fromMinutes, int toMinutes, boolean toAtLeast) {
	}

	static final WaitEstimate NO_DATA = new WaitEstimate(null, false, null, Freshness.NO_DATA, null);

	/** Czy można tym szacunkiem uszeregować SOR. */
	public boolean isUsable() {
		return freshness == Freshness.FRESH && minutes != null;
	}

}
