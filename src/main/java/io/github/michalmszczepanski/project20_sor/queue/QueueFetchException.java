package io.github.michalmszczepanski.project20_sor.queue;

import java.io.Serial;

public class QueueFetchException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	public QueueFetchException(String message) {
		super(message);
	}

	public QueueFetchException(String message, Throwable cause) {
		super(message, cause);
	}

}
