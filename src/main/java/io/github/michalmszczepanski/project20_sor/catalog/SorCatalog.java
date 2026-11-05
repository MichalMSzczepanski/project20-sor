package io.github.michalmszczepanski.project20_sor.catalog;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Aktualna lista SOR w pamięci; nieudane odświeżenie zostawia poprzednią. */
@Component
public class SorCatalog {

	private static final Logger log = LoggerFactory.getLogger(SorCatalog.class);

	private final SorCatalogSource source;

	private volatile List<SorUnit> units = List.of();

	public SorCatalog(SorCatalogSource source) {
		this.source = source;
	}

	public void refresh() {
		try {
			List<SorUnit> fetched = source.fetchAll();
			if (fetched.isEmpty()) {
				log.warn("Źródło zwróciło pustą listę SOR — zostawiam poprzednią ({} SOR)", units.size());
				return;
			}
			units = fetched;
			log.info("Wczytano listę SOR: {} oddziałów, {} kolejek", fetched.size(), queueIds().size());
		}
		catch (RuntimeException ex) {
			log.error("Nie udało się odświeżyć listy SOR — zostawiam poprzednią ({} SOR)", units.size(), ex);
		}
	}

	public List<SorUnit> units() {
		return units;
	}

	/** Unikalne numery kolejek TOPSOR (kilka SOR może dzielić jedną kolejkę). */
	public Set<String> queueIds() {
		return units.stream().map(SorUnit::queueId).collect(Collectors.toUnmodifiableSet());
	}

}
