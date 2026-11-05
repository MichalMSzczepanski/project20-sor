package io.github.michalmszczepanski.project20_sor.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalDouble;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CoordinateParserTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	@ParameterizedTest
	@ValueSource(strings = { "19.1853588", "\"19.1853588\"", "\"19.1853588,\"", "\" 19.1853588 \"", "\"19,1853588\"" })
	void parsesNumbersAndRepairsObviousTextIssues(String json) {
		assertThat(parse(json)).hasValue(19.1853588);
	}

	@ParameterizedTest
	@ValueSource(strings = { "null", "\"\"", "\"abc\"", "\"19.18.53\"", "\"52.1, 21.0\"", "true", "[19.1]" })
	void rejectsEverythingElse(String json) {
		assertThat(parse(json)).isEmpty();
	}

	private static OptionalDouble parse(String json) {
		JsonNode node = MAPPER.readTree(json);
		return CoordinateParser.parse(node);
	}

}
