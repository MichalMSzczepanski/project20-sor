package io.github.michalmszczepanski.project20_sor.route;

import java.time.Instant;
import java.util.List;

/**
 * Trasa do SOR do narysowania na mapie.
 *
 * @param minutes    czas w drodze (dla komunikacji: od wyjścia z domu do dotarcia na miejsce)
 * @param distanceKm długość trasy samochodem; dla komunikacji {@code null}
 * @param departure  kiedy wyjść (komunikacja); dla samochodu {@code null}
 * @param arrival    kiedy będziemy na miejscu (komunikacja); dla samochodu {@code null}
 * @param transfers  liczba przesiadek (komunikacja)
 */
public record Route(RouteMode mode, int minutes, Double distanceKm, Instant departure, Instant arrival,
		int transfers, List<Leg> legs) {

	public Route {
		legs = List.copyOf(legs);
	}

	/**
	 * Odcinek trasy.
	 *
	 * @param mode tryb ze źródła (np. CAR, WALK, BUS, TRAM, SUBWAY, RAIL)
	 * @param line numer linii, jeśli jest
	 * @param path punkty [szerokość, długość]
	 */
	public record Leg(String mode, String line, String from, String to, Instant start, Instant end, int minutes,
			List<double[]> path) {

		public Leg {
			path = List.copyOf(path);
		}

	}

}
