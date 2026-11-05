package io.github.michalmszczepanski.project20_sor.ranking;

import java.util.List;

/**
 * @param ranked      SOR ze świeżymi danymi w rozsądnej odległości, od najkrótszego łącznego czasu
 * @param withoutData SOR bez aktualnych danych w zasięgu rankingu — żeby nikt nie przeoczył bliskiego SOR,
 *                    o którego kolejce nic nie wiemy
 * @param fartherFaster SOR spoza rozsądnej odległości, które mimo to wyraźnie skracają łączny czas — osobno,
 *                      bo kolejka zdąży się zmienić, zanim pacjent dojedzie
 * @param sorTypesKnown czy rozróżniamy SOR dla dzieci; dopóki nie — dorosłemu mogą trafić się dziecięce
 */
public record Recommendation(List<SorRanking> ranked, List<SorRanking> withoutData, List<SorRanking> fartherFaster,
		boolean sorTypesKnown) {
}
