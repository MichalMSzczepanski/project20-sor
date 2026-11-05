package io.github.michalmszczepanski.project20_sor.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RpwdlSorTypeSourceTest {

	private static final String EXPORT = "/api/rpwdl-public-resource/ledgers-zip/initial?fileTypeInsideZip=CSV";

	/** Prawdziwe wiersze eksportu z 05.11.2026: Szpital Dziecięcy im. Bogdanowicza (4903) i SOR w Lubinie (4902). */
	private static final Map<String, SorType> EXPECTED = Map.of(
			"000000007152-011", SorType.PEDIATRIC,
			"000000025802-079", SorType.GENERAL);

	private final RestClient.Builder builder = RestClient.builder().baseUrl("https://rpwdl.test");

	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

	private final RpwdlSorTypeSource source = new RpwdlSorTypeSource(builder.build(), EXPORT);

	@Test
	void readsSorTypesFromRealExportRows() throws IOException {
		Map<String, SorType> types = source.read(new ByteArrayInputStream(zip("Info.txt", "podmioty.csv", "komorki.csv")));

		// Poradnia anestezjologiczna z tej samej księgi nie jest SOR — nie trafia do wyniku.
		assertThat(types).isEqualTo(EXPECTED);
	}

	@Test
	void orderOfFilesInZipDoesNotMatter() throws IOException {
		assertThat(source.read(new ByteArrayInputStream(zip("komorki.csv", "podmioty.csv")))).isEqualTo(EXPECTED);
	}

	@Test
	void failsWithoutLedgerFile() throws IOException {
		assertThatThrownBy(() -> source.read(new ByteArrayInputStream(zip("komorki.csv"))))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("podmioty.csv");
	}

	@Test
	void downloadsExport() throws IOException {
		server.expect(requestTo("https://rpwdl.test" + EXPORT))
				.andRespond(withSuccess(zip("podmioty.csv", "komorki.csv"), MediaType.APPLICATION_OCTET_STREAM));

		assertThat(source.fetch()).isEqualTo(EXPECTED);
		server.verify();
	}

	@Test
	void failsOnHttpError() {
		server.expect(requestTo("https://rpwdl.test" + EXPORT)).andRespond(withServiceUnavailable());

		assertThatThrownBy(source::fetch).isInstanceOf(IllegalStateException.class).hasMessageContaining("503");
	}

	@Test
	void failsWhenExportHasNoSorCells() throws IOException {
		server.expect(requestTo("https://rpwdl.test" + EXPORT))
				.andRespond(withSuccess(zip("podmioty.csv"), MediaType.APPLICATION_OCTET_STREAM));

		assertThatThrownBy(source::fetch).isInstanceOf(IllegalStateException.class).hasMessageContaining("SOR");
	}

	@Test
	void padsBookNumberToTwelveDigits() {
		assertThat(RpwdlSorTypeSource.queueId("7152", "011")).isEqualTo("000000007152-011");
		assertThat(RpwdlSorTypeSource.queueId("000000007152", "011")).isEqualTo("000000007152-011");
	}

	private static byte[] zip(String... files) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			for (String file : files) {
				zip.putNextEntry(new ZipEntry(file));
				zip.write("Info.txt".equals(file)
						? "Raport wygenerowany na podstawie danych w systemie RPWDL".getBytes(StandardCharsets.UTF_8)
						: new ClassPathResource("fixtures/rpwdl/" + file).getContentAsByteArray());
				zip.closeEntry();
			}
		}
		return bytes.toByteArray();
	}

}
