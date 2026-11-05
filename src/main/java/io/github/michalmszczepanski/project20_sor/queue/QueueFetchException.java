package io.github.michalmszczepanski.project20_sor.queue;

public class QueueFetchException extends RuntimeException {

	public QueueFetchException(String message) {
		super(message);
	}

	public QueueFetchException(String message, Throwable cause) {
		super(message, cause);
	}

}
