package io.github.michalmszczepanski.project20_sor.route;

import java.util.ArrayList;
import java.util.List;

/** Dekoder formatu „encoded polyline” (Google), z wybieraną precyzją — Transitous podaje 6 miejsc po przecinku. */
final class Polyline {

	private Polyline() {
	}

	/** @return punkty jako [szerokość, długość] */
	static List<double[]> decode(String encoded, int precision) {
		List<double[]> points = new ArrayList<>();
		double factor = Math.pow(10, precision);
		int index = 0;
		int lat = 0;
		int lng = 0;
		while (index < encoded.length()) {
			int[] latResult = nextValue(encoded, index);
			lat += latResult[0];
			int[] lngResult = nextValue(encoded, latResult[1]);
			lng += lngResult[0];
			index = lngResult[1];
			points.add(new double[] { lat / factor, lng / factor });
		}
		return points;
	}

	/** @return [wartość, indeks następnego znaku] */
	private static int[] nextValue(String encoded, int start) {
		int result = 0;
		int shift = 0;
		int index = start;
		int chunk;
		do {
			if (index >= encoded.length()) {
				throw new IllegalArgumentException("Urwany zapis polyline");
			}
			chunk = encoded.charAt(index++) - 63;
			result |= (chunk & 0x1f) << shift;
			shift += 5;
		}
		while (chunk >= 0x20);
		int value = (result & 1) != 0 ? ~(result >> 1) : result >> 1;
		return new int[] { value, index };
	}

}
