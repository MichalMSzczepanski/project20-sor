package io.github.michalmszczepanski.project20_sor.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sor")
public record SorProperties(PacjentGov pacjentGov, Catalog catalog, Rpwdl rpwdl, Queue queue, Travel travel,
		Ranking ranking, Health health, Geocoding geocoding, Transit transit) {

	/**
	 * @param baseUrl   adres portalu pacjent.gov.pl
	 * @param userAgent jawny podpis klienta, żeby administrator źródła wiedział, kto pyta
	 * @param timeout   limit czasu pojedynczego zapytania
	 */
	public record PacjentGov(String baseUrl, String userAgent, Duration timeout) {
	}

	/**
	 * @param pagePath        strona z mapą SOR — z niej odczytujemy aktualną ścieżkę pliku z listą
	 * @param path            ścieżka zapasowa, gdy ze strony nie da się jej odczytać
	 * @param refreshInterval co ile odświeżać listę
	 * @param retryInterval   co ile ponawiać, dopóki listy nie udało się wczytać ani razu
	 */
	public record Catalog(String pagePath, String path, Duration refreshInterval, Duration retryInterval) {
	}

	/**
	 * @param baseUrl         serwer RPWDL
	 * @param exportPath      otwarty eksport CSV podmiotów leczniczych (ZIP ~35 MB, link z dane.gov.pl)
	 * @param timeout         limit czasu pobierania eksportu
	 * @param refreshInterval co ile odświeżać (rodzaje SOR zmieniają się rzadko)
	 * @param retryInterval   co ile ponawiać, dopóki nie udało się wczytać ani razu
	 * @param snapshotFile    plik z ostatnim udanym wynikiem, odtwarzany przy starcie (tymczasowo, do czasu cache)
	 */
	public record Rpwdl(String baseUrl, String exportPath, Duration timeout, Duration refreshInterval,
			Duration retryInterval, String snapshotFile) {
	}

	/**
	 * @param pollInterval       co ile zaczyna się pełny przebieg po wszystkich SOR
	 * @param initialDelay       opóźnienie pierwszego przebiegu (lista SOR musi się wcześniej wczytać)
	 * @param spreadOver         na ile rozłożyć zapytania jednego przebiegu (jedno po drugim, równe odstępy)
	 * @param noDataRetryDelay   po jakim czasie ponowić kolejki, dla których źródło chwilowo nie miało danych
	 * @param staleAfter         po jakim czasie od wygenerowania pomiar uznajemy za nieaktualny
	 * @param startupConcurrency ile zapytań naraz w pierwszym przebiegu po starcie (żeby szybko mieć dane)
	 */
	public record Queue(Duration pollInterval, Duration initialDelay, Duration spreadOver, Duration noDataRetryDelay,
			Duration staleAfter, int startupConcurrency) {

		public Queue {
			// Przebieg z ponowieniem musi się zmieścić w odstępie, inaczej kolejne przebiegi by się opóźniały.
			if (pollInterval != null && spreadOver != null && noDataRetryDelay != null
					&& spreadOver.plus(noDataRetryDelay).compareTo(pollInterval) >= 0) {
				throw new IllegalArgumentException("sor.queue.spread-over + no-data-retry-delay musi być krótsze niż poll-interval");
			}
		}

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
	 * Progi objazdu są tymczasowe — do czasu prognozy kolejki w chwili przyjazdu na podstawie historii.
	 *
	 * @param candidates          ilu najbliższych (w linii prostej) SOR rozważać — tylu dotyczy zapytanie o trasy
	 * @param maxWithoutData      ile bliższych SOR bez aktualnych danych pokazać obok rankingu
	 * @param maxExtraTravel      o ile dłuższy może być dojazd od dojazdu do najbliższego SOR z danymi,
	 *                            żeby SOR trafił do głównego rankingu
	 * @param minSavingForFarther ile musi zaoszczędzić dalszy SOR względem najlepszego z rankingu,
	 *                            żeby pokazać go osobno („dalej, ale krócej się czeka”)
	 * @param maxFarther          ile takich dalszych SOR pokazać
	 */
	public record Ranking(int candidates, int maxWithoutData, Duration maxExtraTravel, Duration minSavingForFarther,
			int maxFarther) {
	}

	/**
	 * @param minFreshShare poniżej jakiego udziału kolejek ze świeżymi danymi serwis uznajemy za niesprawny
	 */
	public record Health(double minFreshShare) {
	}

	/**
	 * @param baseUrl     serwer Nominatim (publiczny: najwyżej 1 zapytanie/s, nie do dużego ruchu)
	 * @param timeout     limit czasu — użytkownik czeka na wynik
	 * @param minInterval minimalny odstęp między zapytaniami
	 * @param cacheSize   ile ostatnich wyszukiwań pamiętać
	 */
	public record Geocoding(String baseUrl, Duration timeout, Duration minInterval, int cacheSize) {
	}

	/**
	 * @param baseUrl serwer Transitous / MOTIS (publiczny: tylko projekty otwarte i niekomercyjne)
	 * @param timeout limit czasu wyznaczania trasy
	 */
	public record Transit(String baseUrl, Duration timeout) {
	}

}
