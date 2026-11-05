package io.github.michalmszczepanski.project20_sor.catalog;

/** Rodzaj SOR według kodu specjalności komórki w RPWDL (część VIII kodu resortowego). */
public enum SorType {

	/** Kod 4902 — szpitalny oddział ratunkowy. */
	GENERAL,
	/** Kod 4903 — szpitalny oddział ratunkowy dla dzieci. */
	PEDIATRIC,
	/** Komórki nie ma w rejestrze albo ma inny kod (błąd w liście SOR lub rejestrze). */
	UNKNOWN

}
