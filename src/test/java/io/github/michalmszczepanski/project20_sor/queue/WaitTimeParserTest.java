package io.github.michalmszczepanski.project20_sor.queue;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WaitTimeParserTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			// formy potwierdzone w odpowiedziach źródła
			"-                | 0   | false",
			"2 min            | 2   | false",
			"9 min            | 9   | false",
			"powyżej 2 godzin | 120 | true",
			// formy przewidywane — źródło może je podać
			"Powyżej 1 godziny | 60  | true",
			"1 godz. 20 min   | 80  | false",
			"45 minut         | 45  | false",
			"2h               | 120 | false" })
	void parsesKnownForms(String raw, int minutes, boolean atLeast) {
		WaitTime wait = WaitTimeParser.parse(raw);

		assertThat(wait.minutes()).isEqualTo(minutes);
		assertThat(wait.atLeast()).isEqualTo(atLeast);
		assertThat(wait.raw()).isEqualTo(raw);
	}

	@Test
	void unknownTextGivesUnknownTime() {
		WaitTime wait = WaitTimeParser.parse("brak danych");

		assertThat(wait.isKnown()).isFalse();
		assertThat(wait.raw()).isEqualTo("brak danych");
	}

	@Test
	void nullGivesUnknownTime() {
		assertThat(WaitTimeParser.parse(null).isKnown()).isFalse();
	}

}
