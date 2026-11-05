package io.github.michalmszczepanski.project20_sor.ranking;

import java.util.List;

/**
 * @param ranked      SOR ze świeżymi danymi, od najkrótszego łącznego czasu
 * @param withoutData SOR bez aktualnych danych, do których dojazd jest krótszy niż do najdalszego z rankingu —
 *                    żeby nikt nie jechał daleko, nie wiedząc, że bliżej jest SOR, o którego kolejce nic nie wiemy
 * @param sorTypesKnown czy rozróżniamy SOR dla dzieci; dopóki nie — dorosłemu mogą trafić się dziecięce
 */
public record Recommendation(List<SorRanking> ranked, List<SorRanking> withoutData, boolean sorTypesKnown) {
}
