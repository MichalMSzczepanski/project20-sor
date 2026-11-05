package io.github.michalmszczepanski.project20_sor.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GeoPointTest {

	private static final GeoPoint WARSZAWA = new GeoPoint(52.2297, 21.0122);

	private static final GeoPoint KRAKOW = new GeoPoint(50.0647, 19.9450);

	@Test
	void distanceWarsawKrakowIsAbout252Km() {
		assertThat(WARSZAWA.distanceKmTo(KRAKOW)).isCloseTo(252, within(2.0));
		assertThat(WARSZAWA.distanceKmTo(WARSZAWA)).isZero();
	}

	@Test
	void rejectsInvalidCoordinates() {
		assertThatThrownBy(() -> new GeoPoint(91, 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new GeoPoint(0, 181)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new GeoPoint(Double.NaN, 0)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void straightLineEstimateAppliesDetourAndSpeed() {
		TravelEstimate estimate = new StraightLineTravelEstimator(1.5, 60).estimate(WARSZAWA, KRAKOW);

		// ~252 km × 1,5 / 60 km/h ≈ 378 min
		assertThat(estimate.minutes()).isBetween(374, 382);
		assertThat(estimate.method()).isEqualTo(TravelEstimate.Method.STRAIGHT_LINE);
	}

}
