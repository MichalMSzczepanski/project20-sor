package io.github.michalmszczepanski.project20_sor.queue;

/**
 * Czas oczekiwania z TOPSOR.
 *
 * @param minutes liczba minut; {@code null}, gdy tekstu źródła nie udało się zrozumieć
 * @param atLeast źródło podało tylko dolną granicę („powyżej 2 godzin”)
 * @param raw     tekst źródła bez zmian
 */
public record WaitTime(Integer minutes, boolean atLeast, String raw) {

	public boolean isKnown() {
		return minutes != null;
	}

}
