package io.github.michalmszczepanski.project20_sor.route;

import java.io.Serial;

public class RoutingException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	public RoutingException(String message, Throwable cause) {
		super(message, cause);
	}

}
