package io.github.michalmszczepanski.project20_sor.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sor")
public record SorProperties(PacjentGov pacjentGov, Catalog catalog, Queue queue, Travel travel, Ranking ranking) {

	/**
	 * @param baseUrl   adres portalu pacjent.gov.pl
	 * @param userAgent jawny podpis klienta, żeby administrator źródła wiedział, kto pyta
	 * @param timeout   limit czasu pojedynczego zapytania
	 */
	public record PacjentGov(String baseUrl, String userAgent, Duration timeout) {
	}

	/**
	 * @param path            ścieżka pliku z listą SOR (data w nazwie zmienia się przy aktualizacji)
	 * @param refreshInterval co ile odświeżać listę
	 * @param retryInterval   co ile ponawiać, dopóki listy nie udało się wczytać ani razu
	 */
	public record Catalog(String path, Duration refreshInterval, Duration retryInterval) {
	}

	/**
	 * @param pollInterval odstęp między kolejnymi pełnymi przebiegami po wszystkich SOR
	 * @param initialDelay opóźnienie pierwszego przebiegu (lista SOR musi się wcześniej wczytać)
	 * @param concurrency  ile zapytań do TOPSOR naraz
	 * @param staleAfter   po jakim czasie od wygenerowania pomiar uznajemy za nieaktualny
	 */
	public record Queue(Duration pollInterval, Duration initialDelay, int concurrency, Duration staleAfter) {
	}

	/**
	 * @param detourFactor    ile razy droga jest dłuższa od linii prostej (szacunek zapasowy)
	 * @param averageSpeedKmh średnia prędkość dojazdu (szacunek zapasowy)
	 * @param osrm            czas przejazdu po drogach
	 */
	public record Travel(double detourFactor, double averageSpeedKmh, Osrm osrm) {
	}

	/**
	 * @param enabled czy pytać OSRM; wyłączony albo niedostępny → szacunek z linii prostej
	 * @param baseUrl serwer OSRM (publiczny demo-serwer nie jest do ruchu produkcyjnego)
	 * @param timeout limit czasu — użytkownik czeka na odpowiedź, więc krótki
	 */
	public record Osrm(boolean enabled, String baseUrl, Duration timeout) {
	}

	/**
	 * @param candidates     ilu najbliższych (w linii prostej) SOR rozważać — tylu dotyczy zapytanie o trasy
	 * @param maxWithoutData ile bliższych SOR bez aktualnych danych pokazać obok rankingu
	 */
	public record Ranking(int candidates, int maxWithoutData) {
	}

}
