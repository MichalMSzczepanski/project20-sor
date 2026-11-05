package io.github.michalmszczepanski.project20_sor.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.michalmszczepanski.project20_sor.catalog.SorType;
import io.github.michalmszczepanski.project20_sor.catalog.SorUnit;
import io.github.michalmszczepanski.project20_sor.geo.GeoPoint;
import io.github.michalmszczepanski.project20_sor.geo.TravelEstimate;
import io.github.michalmszczepanski.project20_sor.queue.QueueSnapshot;
import io.github.michalmszczepanski.project20_sor.queue.QueueStage;
import io.github.michalmszczepanski.project20_sor.queue.QueueState;
import io.github.michalmszczepanski.project20_sor.queue.WaitTime;
import io.github.michalmszczepanski.project20_sor.ranking.PatientGroup;
import io.github.michalmszczepanski.project20_sor.ranking.RankingService;
import io.github.michalmszczepanski.project20_sor.ranking.Recommendation;
import io.github.michalmszczepanski.project20_sor.ranking.SorRanking;
import io.github.michalmszczepanski.project20_sor.ranking.TriagePriority;
import io.github.michalmszczepanski.project20_sor.ranking.WaitEstimate;

@WebMvcTest(SorController.class)
class SorControllerTest {

	private static final Instant GENERATED = Instant.parse("2026-11-05T17:37:58Z");

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private RankingService rankingService;

	@Test
	void returnsRanking() throws Exception {
		given(rankingService.recommend(any(GeoPoint.class), eq(TriagePriority.MEDIUM), eq(PatientGroup.ADULT), eq(5)))
				.willReturn(new Recommendation(List.of(ranking()), List.of(), List.of(), true));

		mvc.perform(get("/api/sor/nearest").param("lat", "51.2").param("lng", "15.5").param("priority", "MEDIUM")
				.param("limit", "5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ranked[0].queueId").value("000000001137-014"))
				.andExpect(jsonPath("$.ranked[0].city").value("BOLESŁAWIEC"))
				.andExpect(jsonPath("$.ranked[0].address").value("Jeleniogórska 4, 59-700 BOLESŁAWIEC"))
				.andExpect(jsonPath("$.ranked[0].distanceKm").value(7.3))
				.andExpect(jsonPath("$.ranked[0].travelMinutes").value(11))
				.andExpect(jsonPath("$.ranked[0].waitMinutes").value(2))
				.andExpect(jsonPath("$.ranked[0].waitRange.fromMinutes").value(2))
				.andExpect(jsonPath("$.ranked[0].waitRange.toMinutes").value(30))
				.andExpect(jsonPath("$.fartherFaster").isEmpty())
				.andExpect(jsonPath("$.ranked[0].totalMinutes").value(13))
				.andExpect(jsonPath("$.ranked[0].freshness").value("FRESH"))
				.andExpect(jsonPath("$.ranked[0].patientsPresent").value(18))
				.andExpect(jsonPath("$.ranked[0].stages[0].name").value("Rejestracja"))
				.andExpect(jsonPath("$.ranked[0].stages[0].waitText").value("2 min"))
				.andExpect(jsonPath("$.ranked[0].stages[0].waitMinutes").value(2))
				.andExpect(jsonPath("$.ranked[0].travelMethod").value("ROAD"))
				.andExpect(jsonPath("$.ranked[0].sorType").value("GENERAL"))
				.andExpect(jsonPath("$.withoutData").isEmpty())
				.andExpect(jsonPath("$.sorTypesKnown").value(true));
	}

	@Test
	void passesPatientGroup() throws Exception {
		given(rankingService.recommend(any(GeoPoint.class), eq(null), eq(PatientGroup.CHILD), eq(10)))
				.willReturn(new Recommendation(List.of(), List.of(), List.of(), true));

		mvc.perform(get("/api/sor/nearest").param("lat", "51").param("lng", "15").param("patient", "CHILD"))
				.andExpect(status().isOk());
	}

	@Test
	void rejectsUnknownPatientGroup() throws Exception {
		mvc.perform(get("/api/sor/nearest").param("lat", "51").param("lng", "15").param("patient", "SENIOR"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsInvalidCoordinates() throws Exception {
		mvc.perform(get("/api/sor/nearest").param("lat", "95").param("lng", "15.5")).andExpect(status().isBadRequest());
	}

	@Test
	void rejectsUnknownPriority() throws Exception {
		mvc.perform(get("/api/sor/nearest").param("lat", "51").param("lng", "15").param("priority", "PILNE"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsLimitOutOfRange() throws Exception {
		mvc.perform(get("/api/sor/nearest").param("lat", "51").param("lng", "15").param("limit", "0"))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/sor/nearest").param("lat", "51").param("lng", "15").param("limit", "51"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void requiresCoordinates() throws Exception {
		mvc.perform(get("/api/sor/nearest").param("lat", "51")).andExpect(status().isBadRequest());
	}

	private static SorRanking ranking() {
		SorUnit unit = new SorUnit("000000001137-014", "ZESPÓŁ OPIEKI ZDROWOTNEJ W BOLESŁAWCU", "Szpitalny Odział Ratunkowy",
				new SorUnit.Address("Jeleniogórska", "4", "", "59-700", "BOLESŁAWIEC"), "Dolnośląskie", "", "",
				new GeoPoint(51.25449, 15.56694));
		Map<QueueStage, QueueSnapshot.Stage> stages = new EnumMap<>(QueueStage.class);
		stages.put(QueueStage.REGISTRATION, new QueueSnapshot.Stage("Rejestracja", 3, new WaitTime(2, false, "2 min")));
		QueueSnapshot snapshot = new QueueSnapshot(unit.queueId(), GENERATED, GENERATED, 18, stages, List.of());
		return new SorRanking(unit, SorType.GENERAL, new TravelEstimate(7.26, 11, TravelEstimate.Method.ROAD),
				new WaitEstimate(2, false, new WaitEstimate.Range(2, 30, false), WaitEstimate.Freshness.FRESH, GENERATED),
				new QueueState(snapshot, null, null));
	}

}
