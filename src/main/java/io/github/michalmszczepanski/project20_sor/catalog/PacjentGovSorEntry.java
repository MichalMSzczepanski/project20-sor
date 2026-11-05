package io.github.michalmszczepanski.project20_sor.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

/** Wpis pliku Mapy-SOR-*.json z pacjent.gov.pl (surowe pola, nazwy jak w źródle; współrzędne bywają tekstem). */
@JsonIgnoreProperties(ignoreUnknown = true)
record PacjentGovSorEntry(
		@JsonProperty("numer_ksiegi") String bookNumber,
		@JsonProperty("nazwa_swd") String hospitalName,
		@JsonProperty("nazwa_miejsca") String unitName,
		@JsonProperty("adr_lok_ulica") String street,
		@JsonProperty("adr_lok_nr_domu") String buildingNo,
		@JsonProperty("adr_lok_nr_lokalu") String localNo,
		@JsonProperty("adr_lok_kod_poczt") String postalCode,
		@JsonProperty("adr_lok_miejsc") String city,
		@JsonProperty("Województwo") String voivodeship,
		@JsonProperty("telefon_rej") String phone,
		@JsonProperty("uwagi") String notes,
		@JsonProperty("lat") JsonNode lat,
		@JsonProperty("lng") JsonNode lng) {
}
