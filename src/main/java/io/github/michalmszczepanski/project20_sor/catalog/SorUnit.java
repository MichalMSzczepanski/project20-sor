package io.github.michalmszczepanski.project20_sor.catalog;

import io.github.michalmszczepanski.project20_sor.geo.GeoPoint;

/**
 * Jeden SOR z listy pacjent.gov.pl.
 *
 * @param queueId      numer księgi rejestrowej z częścią kodu — klucz kolejki w TOPSOR; kilka SOR może dzielić jeden numer
 * @param hospitalName nazwa podmiotu
 * @param unitName     nazwa komórki (np. „Szpitalny Oddział Ratunkowy”)
 * @param notes        uwagi źródła (np. „całodobowo”)
 */
public record SorUnit(String queueId, String hospitalName, String unitName, Address address, String voivodeship,
		String phone, String notes, GeoPoint location) {

	public record Address(String street, String buildingNo, String localNo, String postalCode, String city) {

		public String formatted() {
			StringBuilder sb = new StringBuilder();
			if (!street.isBlank()) {
				sb.append(street);
				if (!buildingNo.isBlank()) {
					sb.append(' ').append(buildingNo);
					if (!localNo.isBlank()) {
						sb.append('/').append(localNo);
					}
				}
				sb.append(", ");
			}
			return sb.append((postalCode + ' ' + city).trim()).toString();
		}

	}

}
