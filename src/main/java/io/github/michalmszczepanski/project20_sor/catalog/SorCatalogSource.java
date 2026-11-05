package io.github.michalmszczepanski.project20_sor.catalog;

@FunctionalInterface
public interface SorCatalogSource {

	SorCatalogData fetch();

}
