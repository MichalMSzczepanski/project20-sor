package io.github.michalmszczepanski.project20_sor.catalog;

import java.util.Optional;

/** Znajduje ścieżkę aktualnego pliku z listą SOR (nazwa zmienia się z każdą aktualizacją). */
@FunctionalInterface
public interface CatalogFileLocator {

	/** @return ścieżka pliku albo pusta, gdy nie udało się jej ustalić */
	Optional<String> locate();

}
