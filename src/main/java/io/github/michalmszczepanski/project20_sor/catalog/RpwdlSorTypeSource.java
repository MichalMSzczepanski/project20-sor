package io.github.michalmszczepanski.project20_sor.catalog;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import io.github.michalmszczepanski.project20_sor.config.SorProperties;
import tools.jackson.databind.MappingIterator;
import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvSchema;

/**
 * Rodzaje SOR z otwartego eksportu Rejestru Podmiotów Wykonujących Działalność Leczniczą (dane.gov.pl).
 * ZIP z plikami CSV: {@code podmioty.csv} (ID księgi → numer księgi) i {@code komorki.csv} (kod komórki,
 * kod specjalności VIII). Numer kolejki TOPSOR to numer księgi + kod komórki — sprawdzone na 248 z 250 SOR
 * z listy pacjent.gov.pl (05.11.2026). Czytamy strumieniowo, kolejność plików w ZIP nie ma znaczenia.
 */
@Component
class RpwdlSorTypeSource implements SorTypeSource {

	static final String SOR_CODE = "4902";

	static final String PEDIATRIC_SOR_CODE = "4903";

	private static final CsvSchema SCHEMA = CsvSchema.emptySchema().withHeader().withColumnSeparator(';');

	private final RestClient restClient;

	private final String exportPath;

	private final CsvMapper csvMapper = CsvMapper.shared();

	@Autowired
	RpwdlSorTypeSource(RestClient rpwdlRestClient, SorProperties properties) {
		this(rpwdlRestClient, properties.rpwdl().exportPath());
	}

	RpwdlSorTypeSource(RestClient restClient, String exportPath) {
		this.restClient = restClient;
		this.exportPath = exportPath;
	}

	@Override
	public Map<String, SorType> fetch() {
		Map<String, SorType> types = restClient.get()
				.uri(exportPath)
				.accept(MediaType.ALL)
				.exchange((request, response) -> {
					if (!response.getStatusCode().is2xxSuccessful()) {
						throw new IllegalStateException("RPWDL: HTTP " + response.getStatusCode().value());
					}
					return read(response.getBody());
				});
		if (types == null || types.isEmpty()) {
			throw new IllegalStateException("RPWDL: w eksporcie nie ma komórek SOR");
		}
		return types;
	}

	Map<String, SorType> read(InputStream zip) {
		Map<String, String> bookNumbers = new HashMap<>();
		List<Cell> sorCells = new ArrayList<>();
		try (ZipInputStream entries = new ZipInputStream(zip)) {
			ZipEntry entry;
			while ((entry = entries.getNextEntry()) != null) {
				switch (entry.getName()) {
					case "podmioty.csv" -> readRows(entries,
							row -> bookNumbers.put(row.get("ID Księgi"), row.get("Numer księgi rejestrowej")));
					case "komorki.csv" -> readRows(entries, row -> {
						String code = row.get("kodResortVIII");
						if (SOR_CODE.equals(code) || PEDIATRIC_SOR_CODE.equals(code)) {
							sorCells.add(new Cell(row.get("ID Księgi"), row.get("Kod komórki"), code));
						}
					});
					default -> {
						// pozostałe pliki eksportu nie są potrzebne
					}
				}
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("RPWDL: nie udało się odczytać eksportu", ex);
		}
		if (bookNumbers.isEmpty()) {
			throw new IllegalStateException("RPWDL: w eksporcie nie ma podmioty.csv");
		}
		Map<String, SorType> types = new HashMap<>();
		for (Cell cell : sorCells) {
			String bookNumber = bookNumbers.get(cell.bookId());
			if (bookNumber == null || cell.cellCode() == null) {
				continue;
			}
			SorType type = PEDIATRIC_SOR_CODE.equals(cell.specialtyCode()) ? SorType.PEDIATRIC : SorType.GENERAL;
			// Gdyby ta sama komórka miała oba kody, ostrożniej uznać ją za dziecięcą — nie polecimy jej dorosłym.
			types.merge(queueId(bookNumber, cell.cellCode()), type,
					(a, b) -> a == SorType.PEDIATRIC || b == SorType.PEDIATRIC ? SorType.PEDIATRIC : a);
		}
		return Map.copyOf(types);
	}

	private void readRows(InputStream csv, Consumer<Map<String, String>> consumer) {
		// Parser zamyka swoje źródło — osłona chroni ZipInputStream, z którego czytamy kolejne pliki.
		try (MappingIterator<Map<String, String>> rows = csvMapper.readerForMapOf(String.class)
				.with(SCHEMA)
				.readValues(new NonClosingInputStream(csv))) {
			rows.forEachRemaining(consumer);
		}
	}

	/** Numer księgi w eksporcie bywa bez zer wiodących — w numerze kolejki ma 12 cyfr. */
	static String queueId(String bookNumber, String cellCode) {
		String digits = bookNumber.trim();
		return "0".repeat(Math.max(0, 12 - digits.length())) + digits + "-" + cellCode.trim();
	}

	private record Cell(String bookId, String cellCode, String specialtyCode) {
	}

	private static final class NonClosingInputStream extends FilterInputStream {

		NonClosingInputStream(InputStream in) {
			super(in);
		}

		@Override
		public void close() {
			// celowo nic — zamyka właściciel strumienia
		}

	}

}
