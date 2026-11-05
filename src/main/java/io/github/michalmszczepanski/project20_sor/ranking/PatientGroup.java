package io.github.michalmszczepanski.project20_sor.ranking;

import io.github.michalmszczepanski.project20_sor.catalog.SorType;

public enum PatientGroup {

	/** SOR dla dzieci nie przyjmują dorosłych — pomijamy je. */
	ADULT {
		@Override
		boolean accepts(SorType type) {
			return type != SorType.PEDIATRIC;
		}
	},
	/** Dziecko przyjmie każdy SOR; dziecięce oznaczamy, ale nie ukrywamy pozostałych. */
	CHILD {
		@Override
		boolean accepts(SorType type) {
			return true;
		}
	};

	abstract boolean accepts(SorType type);

}
