package io.github.michalmszczepanski.project20_sor.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.michalmszczepanski.project20_sor.catalog.SorCatalog;
import io.github.michalmszczepanski.project20_sor.catalog.SorCatalogData;
import io.github.michalmszczepanski.project20_sor.catalog.SorType;
import io.github.michalmszczepanski.project20_sor.catalog.SorTypeRegistry;
import io.github.michalmszczepanski.project20_sor.catalog.SorUnit;
import io.github.michalmszczepanski.project20_sor.config.SorProperties;
import io.github.michalmszczepanski.project20_sor.geo.GeoPoint;
import io.github.michalmszczepanski.project20_sor.geo.StraightLineTravelEstimator;
import io.github.michalmszczepanski.project20_sor.queue.FreshnessPolicy;
import io.github.michalmszczepanski.project20_sor.queue.QueueSnapshot;
import io.github.michalmszczepanski.project20_sor.queue.QueueStage;
import io.github.michalmszczepanski.project20_sor.queue.QueueStateStore;
import io.github.michalmszczepanski.project20_sor.queue.WaitTime;

class RankingServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-05T18:00:00Z");

	/** Punkt startowy; SOR rozstawione na wschód co ~0,1° długości (~6,9 km w linii prostej). */
	private static final GeoPoint ORIGIN = new GeoPoint(52.0, 21.0);

	private final QueueStateStore store = new QueueStateStore();

	private List<SorUnit> units;

	private SorCatalog catalog;

	private final Map<String, SorType> types = new HashMap<>();

	private final SorTypeRegistry sorTypes = new SorTypeRegistry(() -> types, Clock.fixed(NOW, ZoneOffset.UTC));

	@BeforeEach
	void setUp() {
		units = List.of(unit("near", 21.1), unit("middle", 21.2), unit("far", 21.3));
		catalog = new SorCatalog(() -> new SorCatalogData("test", units), Clock.fixed(NOW, ZoneOffset.UTC));
		catalog.refresh();
		sorTypes.refresh();
	}

	/** „near” to SOR dla dzieci, „middle” zwykły, „far” nieznany. */
	private void classifySors() {
		types.put("near", SorType.PEDIATRIC);
		types.put("middle", SorType.GENERAL);
		sorTypes.refresh();
	}

	@Test
	void adultsDoNotGetPediatricSors() {
		classifySors();
		store.recordSnapshot(snapshot("near", NOW, "-", "-", "-"));
		store.recordSnapshot(snapshot("far", NOW, "-", "-", "-"));

		Recommendation recommendation = service(40, 5).recommend(ORIGIN, null, PatientGroup.ADULT, 10);

		assertThat(recommendation.ranked()).extracting(r -> r.unit().queueId()).containsExactly("far");
		assertThat(recommendation.withoutData()).extracting(r -> r.unit().queueId()).containsExactly("middle");
		assertThat(recommendation.sorTypesKnown()).isTrue();
	}

	@Test
	void childrenGetAllSorsWithPediatricMarked() {
		classifySors();
		store.recordSnapshot(snapshot("near", NOW, "-", "-", "-"));
		store.recordSnapshot(snapshot("far", NOW, "-", "-", "-"));

		List<SorRanking> ranked = service(40, 5).recommend(ORIGIN, null, PatientGroup.CHILD, 10).ranked();

		assertThat(ranked).extracting(r -> r.unit().queueId()).containsExactly("near", "far");
		assertThat(ranked).extracting(SorRanking::type).containsExactly(SorType.PEDIATRIC, SorType.UNKNOWN);
	}

	@Test
	void pediatricSorsAreFilteredBeforeChoosingNearestCandidates() {
		classifySors();
		store.recordSnapshot(snapshot("middle", NOW, "-", "-", "-"));

		// Jeden kandydat: gdyby filtr działał po wyborze najbliższych, dorosły dostałby pusty wynik.
		Recommendation recommendation = service(1, 5).recommend(ORIGIN, null, PatientGroup.ADULT, 10);

		assertThat(recommendation.ranked()).extracting(r -> r.unit().queueId()).containsExactly("middle");
	}

	@Test
	void reportsWhenSorTypesAreNotKnownYet() {
		SorTypeRegistry notLoaded = new SorTypeRegistry(Map::of, Clock.fixed(NOW, ZoneOffset.UTC));
		RankingService service = new RankingService(catalog, notLoaded, store, new StraightLineTravelEstimator(1.0, 60),
				new WaitEstimator(new FreshnessPolicy(Duration.ofMinutes(15))), Clock.fixed(NOW, ZoneOffset.UTC),
				new SorProperties.Ranking(40, 5));

		Recommendation recommendation = service.recommend(ORIGIN, null, PatientGroup.ADULT, 10);

		assertThat(recommendation.sorTypesKnown()).isFalse();
		assertThat(recommendation.withoutData()).extracting(r -> r.unit().queueId())
				.containsExactly("near", "middle", "far");
	}

	@Test
	void prefersShorterTotalOverShorterDistance() {
		store.recordSnapshot(snapshot("near", NOW, "5 min", "5 min", "powyżej 2 godzin"));
		store.recordSnapshot(snapshot("middle", NOW, "2 min", "-", "10 min"));
		store.recordSnapshot(snapshot("far", NOW, "-", "-", "-"));

		List<SorRanking> ranked = service(40, 5).recommend(ORIGIN, TriagePriority.MEDIUM, PatientGroup.ADULT, 10).ranked();

		assertThat(ranked).extracting(r -> r.unit().queueId()).containsExactly("far", "middle", "near");
		SorRanking near = ranked.get(2);
		assertThat(near.waitEstimate().minutes()).isEqualTo(130);
		assertThat(near.waitEstimate().atLeast()).isTrue();
		assertThat(ranked.get(1).waitEstimate().minutes()).isEqualTo(12);
	}

	@Test
	void closerSorsWithoutFreshDataAreListedSeparately() {
		store.recordSnapshot(snapshot("near", NOW.minus(Duration.ofMinutes(16)), "-", "-", "-"));
		store.recordSnapshot(snapshot("far", NOW.minus(Duration.ofMinutes(14)), "powyżej 2 godzin", "-", "-"));

		Recommendation recommendation = service(40, 5).recommend(ORIGIN, TriagePriority.MEDIUM, PatientGroup.ADULT, 10);

		assertThat(recommendation.ranked()).extracting(r -> r.unit().queueId()).containsExactly("far");
		assertThat(recommendation.withoutData()).extracting(r -> r.unit().queueId()).containsExactly("near", "middle");
		assertThat(recommendation.withoutData()).extracting(r -> r.waitEstimate().freshness())
				.containsExactly(WaitEstimate.Freshness.STALE, WaitEstimate.Freshness.NO_DATA);
	}

	@Test
	void sorsWithoutDataFartherThanRankedOnesAreNotListed() {
		store.recordSnapshot(snapshot("near", NOW, "-", "-", "-"));

		Recommendation recommendation = service(40, 5).recommend(ORIGIN, TriagePriority.MEDIUM, PatientGroup.ADULT, 10);

		assertThat(recommendation.ranked()).extracting(r -> r.unit().queueId()).containsExactly("near");
		assertThat(recommendation.withoutData()).isEmpty();
	}

	@Test
	void withoutAnyDataShowsNearestByTravel() {
		Recommendation recommendation = service(40, 5).recommend(ORIGIN, null, PatientGroup.ADULT, 2);

		assertThat(recommendation.ranked()).isEmpty();
		assertThat(recommendation.withoutData()).extracting(r -> r.unit().queueId()).containsExactly("near", "middle");
	}

	@Test
	void limitsSorsWithoutDataShownNextToRanking() {
		store.recordSnapshot(snapshot("far", NOW, "-", "-", "-"));

		assertThat(service(40, 1).recommend(ORIGIN, null, PatientGroup.ADULT, 10).withoutData()).extracting(r -> r.unit().queueId())
				.containsExactly("near");
	}

	@Test
	void considersOnlyNearestCandidates() {
		store.recordSnapshot(snapshot("far", NOW, "-", "-", "-"));

		Recommendation recommendation = service(2, 5).recommend(ORIGIN, null, PatientGroup.ADULT, 10);

		assertThat(recommendation.ranked()).isEmpty();
		assertThat(recommendation.withoutData()).extracting(r -> r.unit().queueId()).containsExactly("near", "middle");
	}

	@Test
	void withoutPriorityCountsOnlyRegistrationAndTriage() {
		store.recordSnapshot(snapshot("near", NOW, "3 min", "4 min", "powyżej 2 godzin"));

		WaitEstimate wait = service(40, 5).recommend(ORIGIN, null, PatientGroup.ADULT, 1).ranked().get(0).waitEstimate();

		assertThat(wait.minutes()).isEqualTo(7);
		assertThat(wait.atLeast()).isFalse();
	}

	@Test
	void missingStageMakesWaitUnknown() {
		Map<QueueStage, QueueSnapshot.Stage> stages = new EnumMap<>(QueueStage.class);
		stages.put(QueueStage.REGISTRATION, stage("2 min"));
		store.recordSnapshot(new QueueSnapshot("near", NOW, NOW, 1, stages, List.of()));

		Recommendation recommendation = service(40, 5).recommend(ORIGIN, TriagePriority.HIGH, PatientGroup.ADULT, 10);

		assertThat(recommendation.ranked()).isEmpty();
		assertThat(recommendation.withoutData().get(0).waitEstimate().minutes()).isNull();
	}

	@Test
	void failedPollKeepsLastSnapshot() {
		store.recordSnapshot(snapshot("near", NOW, "-", "-", "-"));
		store.recordFailure("near", "HTTP 503", NOW);

		SorRanking near = service(40, 5).recommend(ORIGIN, TriagePriority.MEDIUM, PatientGroup.ADULT, 1).ranked().get(0);

		assertThat(near.waitEstimate().isUsable()).isTrue();
		assertThat(near.queue().orElseThrow().lastError()).isEqualTo("HTTP 503");
	}

	private RankingService service(int candidates, int maxWithoutData) {
		// 60 km/h bez objazdu: minuty dojazdu ≈ kilometry
		return new RankingService(catalog, sorTypes, store, new StraightLineTravelEstimator(1.0, 60),
				new WaitEstimator(new FreshnessPolicy(Duration.ofMinutes(15))), Clock.fixed(NOW, ZoneOffset.UTC),
				new SorProperties.Ranking(candidates, maxWithoutData));
	}

	private static SorUnit unit(String queueId, double lng) {
		return new SorUnit(queueId, "Szpital " + queueId, "SOR", new SorUnit.Address("", "", "", "", ""), "", "", "",
				new GeoPoint(52.0, lng));
	}

	private static QueueSnapshot snapshot(String queueId, Instant generatedAt, String registration, String triage,
			String medium) {
		Map<QueueStage, QueueSnapshot.Stage> stages = new EnumMap<>(QueueStage.class);
		stages.put(QueueStage.REGISTRATION, stage(registration));
		stages.put(QueueStage.TRIAGE, stage(triage));
		stages.put(QueueStage.PRIORITY_MEDIUM, stage(medium));
		return new QueueSnapshot(queueId, generatedAt, NOW, 5, stages, List.of());
	}

	private static QueueSnapshot.Stage stage(String waitText) {
		WaitTime wait = switch (waitText) {
			case "-" -> new WaitTime(0, false, waitText);
			case "powyżej 2 godzin" -> new WaitTime(120, true, waitText);
			default -> new WaitTime(Integer.parseInt(waitText.split(" ")[0]), false, waitText);
		};
		return new QueueSnapshot.Stage("etap", 1, wait);
	}

}
