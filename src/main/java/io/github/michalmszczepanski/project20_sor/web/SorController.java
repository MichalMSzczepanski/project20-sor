package io.github.michalmszczepanski.project20_sor.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.michalmszczepanski.project20_sor.geo.GeoPoint;
import io.github.michalmszczepanski.project20_sor.ranking.RankingService;
import io.github.michalmszczepanski.project20_sor.ranking.TriagePriority;

@RestController
@RequestMapping("/api/sor")
class SorController {

	static final int MAX_LIMIT = 50;

	private final RankingService rankingService;

	SorController(RankingService rankingService) {
		this.rankingService = rankingService;
	}

	@GetMapping("/nearest")
	RecommendationView nearest(@RequestParam double lat, @RequestParam double lng,
			@RequestParam(required = false) TriagePriority priority, @RequestParam(defaultValue = "10") int limit) {
		if (limit < 1 || limit > MAX_LIMIT) {
			throw new IllegalArgumentException("limit musi być w zakresie 1–" + MAX_LIMIT);
		}
		return RecommendationView.of(rankingService.recommend(new GeoPoint(lat, lng), priority, limit));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail badRequest(IllegalArgumentException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

}
