package io.github.michalmszczepanski.project20_sor.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.michalmszczepanski.project20_sor.catalog.SorUnit;
import io.github.michalmszczepanski.project20_sor.geo.TravelEstimate;
import io.github.michalmszczepanski.project20_sor.queue.QueueSnapshot;
import io.github.michalmszczepanski.project20_sor.queue.QueueState;
import io.github.michalmszczepanski.project20_sor.ranking.SorRanking;
import io.github.michalmszczepanski.project20_sor.ranking.WaitEstimate;

/** Kształt odpowiedzi API — oddzielony od modelu, żeby zmiany w domenie nie zmieniały kontraktu. */
record SorRankingView(String queueId, String hospitalName, String unitName, String address, String voivodeship,
		String phone, String notes, double lat, double lng, double distanceKm, int travelMinutes,
		TravelEstimate.Method travelMethod, Integer waitMinutes,
		boolean waitAtLeast, WaitEstimate.Freshness freshness, Instant dataGeneratedAt, Integer totalMinutes,
		Integer patientsPresent, List<StageView> stages, String lastError) {

	record StageView(String name, Integer patients, String waitText, Integer waitMinutes) {
	}

	static SorRankingView of(SorRanking ranking) {
		SorUnit unit = ranking.unit();
		WaitEstimate wait = ranking.waitEstimate();
		Optional<QueueSnapshot> snapshot = ranking.queue().flatMap(QueueState::latestSnapshot);
		return new SorRankingView(unit.queueId(), unit.hospitalName(), unit.unitName(), unit.address().formatted(),
				unit.voivodeship(), unit.phone(), unit.notes(), unit.location().lat(), unit.location().lng(),
				Math.round(ranking.travel().distanceKm() * 10) / 10.0, ranking.travel().minutes(),
				ranking.travel().method(), wait.minutes(),
				wait.atLeast(), wait.freshness(), wait.generatedAt(), ranking.totalMinutes().orElse(null),
				snapshot.map(QueueSnapshot::patientsPresent).orElse(null), snapshot.map(SorRankingView::stages).orElse(List.of()),
				ranking.queue().map(QueueState::lastError).orElse(null));
	}

	private static List<StageView> stages(QueueSnapshot snapshot) {
		List<QueueSnapshot.Stage> all = new ArrayList<>();
		snapshot.stages().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> all.add(e.getValue()));
		all.addAll(snapshot.otherStages());
		return all.stream()
				.map(s -> new StageView(s.name(), s.patients(), s.waitTime().raw(), s.waitTime().minutes()))
				.toList();
	}

}
