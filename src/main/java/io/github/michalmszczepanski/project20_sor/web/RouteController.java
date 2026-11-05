package io.github.michalmszczepanski.project20_sor.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.michalmszczepanski.project20_sor.geo.GeoPoint;
import io.github.michalmszczepanski.project20_sor.route.RouteMode;
import io.github.michalmszczepanski.project20_sor.route.RouteService;

@RestController
@RequestMapping("/api/route")
class RouteController {

	private final RouteService routeService;

	RouteController(RouteService routeService) {
		this.routeService = routeService;
	}

	@GetMapping
	ResponseEntity<?> route(@RequestParam double fromLat, @RequestParam double fromLng, @RequestParam double toLat,
			@RequestParam double toLng, @RequestParam RouteMode mode) {
		return routeService.plan(new GeoPoint(fromLat, fromLng), new GeoPoint(toLat, toLng), mode)
				.<ResponseEntity<?>>map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, mode == RouteMode.TRANSIT
								? "Nie znaleźliśmy połączenia komunikacją — w tej okolicy może nie być rozkładów."
								: "Nie znaleźliśmy trasy.")));
	}

}
