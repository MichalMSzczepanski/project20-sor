package io.github.michalmszczepanski.project20_sor.geo;

import java.io.Serial;

public class GeocodingException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	public GeocodingException(String message, Throwable cause) {
		super(message, cause);
	}

}
