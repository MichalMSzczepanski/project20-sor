package io.github.michalmszczepanski.project20_sor.catalog;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.michalmszczepanski.project20_sor.config.SorProperties;
import io.github.michalmszczepanski.project20_sor.support.RefreshableValue;
import tools.jackson.databind.json.JsonMapper;

/**
 * Rodzaje SOR (ogólny / dla dzieci) w pamięci; nieudane odświeżenie zostawia poprzednie.
 * Ostatnie udane wczytanie trafia do pliku i jest odtwarzane przy starcie.
 */
@Component
public class SorTypeRegistry implements InitializingBean {

	private static final Logger log = LoggerFactory.getLogger(SorTypeRegistry.class);

	private final SorTypeSource source;

	private final RefreshableValue<Map<String, SorType>> types;

	private final Clock clock;

	private final SorTypeSnapshotStore snapshots;

	@Autowired
	public SorTypeRegistry(SorTypeSource source, Clock clock, SorProperties properties, JsonMapper jsonMapper) {
		this(source, clock, new SorTypeSnapshotFile(Path.of(properties.rpwdl().snapshotFile()), jsonMapper));
	}

	public SorTypeRegistry(SorTypeSource source, Clock clock) {
		this(source, clock, SorTypeSnapshotStore.NONE);
	}

	SorTypeRegistry(SorTypeSource source, Clock clock, SorTypeSnapshotStore snapshots) {
		this.source = source;
		this.clock = clock;
		this.types = new RefreshableValue<>(Map.of(), clock);
		this.snapshots = snapshots;
	}

	/** Przy starcie: ostatni zapisany wynik, zanim pobierzemy świeży z RPWDL. */
	@Override
	public void afterPropertiesSet() {
		snapshots.read().ifPresent(snapshot -> {
			types.set(Map.copyOf(snapshot.types()), snapshot.savedAt());
			log.info("Odtworzono rodzaje SOR z pliku: {} komórek (zapisane {})", snapshot.types().size(),
					snapshot.savedAt());
		});
	}

	public void refresh() {
		try {
			Map<String, SorType> fetched = source.fetch();
			types.set(fetched);
			snapshots.write(fetched, clock.instant());
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
