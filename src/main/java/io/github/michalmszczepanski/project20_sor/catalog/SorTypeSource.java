package io.github.michalmszczepanski.project20_sor.catalog;

import java.util.Map;

public interface SorTypeSource {

	/** @return rodzaj SOR według numeru kolejki (numer księgi rejestrowej + kod komórki, np. 000000007152-011) */
	Map<String, SorType> fetch();

}
