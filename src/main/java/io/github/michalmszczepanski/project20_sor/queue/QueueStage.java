package io.github.michalmszczepanski.project20_sor.queue;

import java.util.Arrays;
import java.util.Optional;

/** Etapy kolejki SOR w TOPSOR; nazwy źródłowe jak w odpowiedzi API pacjent.gov.pl. */
public enum QueueStage {

	REGISTRATION("Rejestracja"),
	TRIAGE("Triaż"),
	PRIORITY_HIGHEST("Priorytet najwyższy"),
	PRIORITY_HIGH("Priorytet wysoki"),
	PRIORITY_MEDIUM("Priorytet średni"),
	PRIORITY_LOW("Priorytet niski"),
	PRIORITY_LOWEST("Priorytet najniższy");

	private final String sourceName;

	QueueStage(String sourceName) {
		this.sourceName = sourceName;
	}

	public String sourceName() {
		return sourceName;
	}

	public static Optional<QueueStage> fromSourceName(String name) {
		String trimmed = name.trim();
		return Arrays.stream(values()).filter(s -> s.sourceName.equals(trimmed)).findFirst();
	}

}
