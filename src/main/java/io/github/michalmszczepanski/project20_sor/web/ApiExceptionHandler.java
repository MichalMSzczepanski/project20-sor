package io.github.michalmszczepanski.project20_sor.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.github.michalmszczepanski.project20_sor.geo.GeocodingException;
import io.github.michalmszczepanski.project20_sor.route.RoutingException;

@RestControllerAdvice
class ApiExceptionHandler {

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail badRequest(IllegalArgumentException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	@ExceptionHandler(RoutingException.class)
	ProblemDetail routingUnavailable(RoutingException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"Serwis tras chwilowo nie odpowiada — użyj nawigacji w Google lub Apple Maps.");
	}

	@ExceptionHandler(GeocodingException.class)
	ProblemDetail geocodingUnavailable(GeocodingException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"Wyszukiwarka adresów chwilowo nie odpowiada — spróbuj ponownie albo kliknij miejsce na mapie.");
	}

}
