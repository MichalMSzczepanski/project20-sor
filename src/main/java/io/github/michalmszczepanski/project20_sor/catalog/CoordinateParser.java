package io.github.michalmszczepanski.project20_sor.catalog;

import java.util.OptionalDouble;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;

/**
 * Współrzędna z listy SOR. Źródło zwykle podaje liczbę, ale bywa tekst z śmieciami
 * (potwierdzone: {@code "19.1853588,"}) — naprawiamy tylko oczywiste przypadki, resztę odrzucamy.
 */
final class CoordinateParser {

	private static final Pattern DECIMAL = Pattern.compile("-?\\d{1,3}(\\.\\d+)?");

	private CoordinateParser() {
	}

	static OptionalDouble parse(JsonNode node) {
		if (node == null || node.isNull()) {
			return OptionalDouble.empty();
		}
		if (node.isNumber()) {
			return OptionalDouble.of(node.doubleValue());
		}
		if (!node.isString()) {
			return OptionalDouble.empty();
		}
		String text = node.stringValue().trim().replaceAll("[,;\\s]+$", "");
		if (!text.contains(".")) {
			// przecinek dziesiętny: "19,1853588"
			text = text.replace(',', '.');
		}
		return DECIMAL.matcher(text).matches() ? OptionalDouble.of(Double.parseDouble(text)) : OptionalDouble.empty();
	}

}
