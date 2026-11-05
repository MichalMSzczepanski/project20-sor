package io.github.michalmszczepanski.project20_sor.web;

import java.util.List;

import io.github.michalmszczepanski.project20_sor.ranking.Recommendation;

record RecommendationView(List<SorRankingView> ranked, List<SorRankingView> withoutData) {

	static RecommendationView of(Recommendation recommendation) {
		return new RecommendationView(recommendation.ranked().stream().map(SorRankingView::of).toList(),
				recommendation.withoutData().stream().map(SorRankingView::of).toList());
	}

}
