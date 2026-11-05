package io.github.michalmszczepanski.project20_sor.queue;

import java.io.Serial;

/**
 * Źródło odpowiedziało poprawnie, ale bez danych kolejki ({@code data: null} albo pusta lista).
 * W pomiarach z 05.11.2026 to zwykle stan chwilowy — za każdym razem dotyczył innych SOR — więc warto ponowić.
 */
public class QueueNoDataException extends QueueFetchException {

	@Serial
	private static final long serialVersionUID = 1L;

	public QueueNoDataException(String message) {
		super(message);
	}

}
