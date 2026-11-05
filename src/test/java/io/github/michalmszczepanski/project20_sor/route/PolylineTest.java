package io.github.michalmszczepanski.project20_sor.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

class PolylineTest {

	@Test
	void decodesExampleFromGoogleDocumentation() {
		// https://developers.google.com/maps/documentation/utilities/polylinealgorithm
		List<double[]> points = Polyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 5);

		assertThat(points).hasSize(3);
		assertThat(points.get(0)[0]).isCloseTo(38.5, within(1e-9));
		assertThat(points.get(0)[1]).isCloseTo(-120.2, within(1e-9));
		assertThat(points.get(1)[0]).isCloseTo(40.7, within(1e-9));
		assertThat(points.get(1)[1]).isCloseTo(-120.95, within(1e-9));
		assertThat(points.get(2)[0]).isCloseTo(43.252, within(1e-9));
		assertThat(points.get(2)[1]).isCloseTo(-126.453, within(1e-9));
	}

	@Test
	void precisionChangesScale() {
		// Ta sama sekwencja przy precyzji 6 daje współrzędne 10 razy mniejsze.
		assertThat(Polyline.decode("_p~iF~ps|U", 6).get(0)[0]).isCloseTo(3.85, within(1e-9));
	}

	@Test
	void emptyStringGivesNoPoints() {
		assertThat(Polyline.decode("", 6)).isEmpty();
	}

	@Test
	void rejectsTruncatedInput() {
		assertThatThrownBy(() -> Polyline.decode("_p~iF~ps|", 5)).isInstanceOf(IllegalArgumentException.class);
	}

}
