package io.github.michalmszczepanski.project20_sor.catalog;

import java.time.Clock;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.github.michalmszczepanski.project20_sor.support.RefreshableValue;

/** Rodzaje SOR (ogólny / dla dzieci) w pamięci; nieudane odświeżenie zostawia poprzednie. */
@Component
public class SorTypeRegistry {

	private static final Logger log = LoggerFactory.getLogger(SorTypeRegistry.class);

	private final SorTypeSource source;

	private final RefreshableValue<Map<String, SorType>> types;

	public SorTypeRegistry(SorTypeSource source, Clock clock) {
		this.source = source;
		this.types = new RefreshableValue<>(Map.of(), clock);
	}

	public void refresh() {
		try {
			Map<String, SorType> fetched = source.fetch();
			types.set(fetched);
			long pediatric = fetched.values().stream().filter(SorType.PEDIATRIC::equals).count();
			log.info("Wczytano rodzaje SOR z RPWDL: {} komórek, w tym {} dla dzieci", fetched.size(), pediatric);
		}
		catch (RuntimeException ex) {
			log.error("Nie udało się wczytać rodzajów SOR z RPWDL — zostawiam poprzednie ({})", types.value().size(), ex);
			types.fail(ex.getMessage());
		}
	}

	public SorType typeOf(String queueId) {
		return types.value().getOrDefault(queueId, SorType.UNKNOWN);
	}

	/** Dopóki nie wczytano rejestru, nie umiemy odróżnić SOR dla dzieci. */
	public boolean isLoaded() {
		return types.isLoaded();
	}

	public int size() {
		return types.value().size();
	}

	public RefreshableValue.Status status() {
		return types.status();
	}

}
