package io.github.michalmszczepanski.project20_sor.queue;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zamienia tekst czasu oczekiwania z TOPSOR na minuty.
 * Potwierdzone w źródle formy: „-” (nikt nie czeka), „2 min”, „powyżej 2 godzin”.
 * Godziny z minutami („1 godz. 20 min”) obsługujemy na wszelki wypadek; nieznany tekst daje czas nieznany.
 */
final class WaitTimeParser {

	private static final Pattern HOURS = Pattern.compile("(\\d+)\\s*(?:h\\b|godz)");

	private static final Pattern MINUTES = Pattern.compile("(\\d+)\\s*min");

	private WaitTimeParser() {
	}

	static WaitTime parse(String raw) {
		if (raw == null) {
			return new WaitTime(null, false, null);
		}
		String text = raw.trim().toLowerCase(Locale.ROOT);
		if ("-".equals(text)) {
			return new WaitTime(0, false, raw);
		}
		Matcher hours = HOURS.matcher(text);
		Matcher minutes = MINUTES.matcher(text);
		boolean hasHours = hours.find();
		boolean hasMinutes = minutes.find();
		if (!hasHours && !hasMinutes) {
			return new WaitTime(null, false, raw);
		}
		int total = (hasHours ? Integer.parseInt(hours.group(1)) * 60 : 0)
				+ (hasMinutes ? Integer.parseInt(minutes.group(1)) : 0);
		return new WaitTime(total, text.startsWith("powyżej"), raw);
	}

}
