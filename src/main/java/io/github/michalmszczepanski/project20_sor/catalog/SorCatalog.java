package io.github.michalmszczepanski.project20_sor.catalog;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.github.michalmszczepanski.project20_sor.support.RefreshableValue;

/** Aktualna lista SOR w pamięci; nieudane odświeżenie zostawia poprzednią. */
@Component
public class SorCatalog {

	private static final Logger log = LoggerFactory.getLogger(SorCatalog.class);

	private final SorCatalogSource source;

	private final RefreshableValue<Loaded> loaded;

	public SorCatalog(SorCatalogSource source, Clock clock) {
		this.source = source;
		this.loaded = new RefreshableValue<>(new Loaded(List.of(), Set.of(), null), clock);
	}

	public void refresh() {
		try {
			SorCatalogData data = source.fetch();
			if (data.units().isEmpty()) {
				loaded.fail("źródło zwróciło pustą listę (" + data.source() + ")");
				return;
			}
			Set<String> queueIds = data.units().stream().map(SorUnit::queueId).collect(Collectors.toUnmodifiableSet());
			String previousSource = loaded.value().source();
			loaded.set(new Loaded(data.units(), queueIds, data.source()));
			if (previousSource != null && !previousSource.equals(data.source())) {
				log.info("Nowa wersja listy SOR: {} (poprzednio {})", data.source(), previousSource);
			}
			log.info("Wczytano listę SOR: {} oddziałów, {} kolejek ({})", data.units().size(), queueIds.size(),
					data.source());
		}
		catch (RuntimeException ex) {
			log.error("Nie udało się odświeżyć listy SOR — zostawiam poprzednią ({} SOR)", units().size(), ex);
			loaded.fail(ex.getMessage());
		}
	}

	public boolean isLoaded() {
		return loaded.isLoaded();
	}

	public List<SorUnit> units() {
		return loaded.value().units();
	}

	/** Unikalne numery kolejek TOPSOR (kilka SOR może dzielić jedną kolejkę). */
	public Set<String> queueIds() {
		return loaded.value().queueIds();
	}

	public Status status() {
		Loaded current = loaded.value();
		RefreshableValue.Status refresh = loaded.status();
		return new Status(current.units().size(), current.queueIds().size(), current.source(), refresh.loadedAt(),
				refresh.lastError(), refresh.lastErrorAt());
	}

	/**
	 * @param source      skąd wczytano obecną listę; {@code null}, gdy jeszcze nic nie wczytano
	 * @param loadedAt    kiedy; {@code null}, gdy jeszcze nic nie wczytano
	 * @param lastError   błąd ostatniego odświeżenia, jeśli było nieudane
	 * @param lastErrorAt kiedy wystąpił
	 */
	public record Status(int units, int queues, String source, Instant loadedAt, String lastError,
			Instant lastErrorAt) {
	}

	private record Loaded(List<SorUnit> units, Set<String> queueIds, String source) {
	}

}
