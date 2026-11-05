package io.github.michalmszczepanski.project20_sor.catalog;

import java.util.List;

/**
 * @param source skąd wczytano listę (np. ścieżka pliku na pacjent.gov.pl)
 */
public record SorCatalogData(String source, List<SorUnit> units) {

	public SorCatalogData {
		units = List.copyOf(units);
	}

}
