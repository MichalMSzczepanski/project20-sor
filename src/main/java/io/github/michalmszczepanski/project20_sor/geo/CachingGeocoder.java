package io.github.michalmszczepanski.project20_sor.geo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import io.github.michalmszczepanski.project20_sor.config.SorProperties;

/**
 * Pamięć podręczna wyników wyszukiwania adresów (najdawniej używane wypadają). Te same adresy wracają często,
 * a każde zapytanie do Nominatim zużywa limit 1/s. Błędów nie zapamiętujemy.
 */
@Component
@Primary
class CachingGeocoder implements Geocoder {

	private final Geocoder delegate;

	private final Map<String, List<Place>> cache;

	@Autowired
	CachingGeocoder(NominatimGeocoder delegate, SorProperties properties) {
		this((Geocoder) delegate, properties.geocoding().cacheSize());
	}

	CachingGeocoder(Geocoder delegate, int maxEntries) {
		this.delegate = delegate;
		this.cache = new LinkedHashMap<>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, List<Place>> eldest) {
				return size() > maxEntries;
			}
		};
	}

	@Override
	public List<Place> search(String query) {
		String key = query.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
		synchronized (cache) {
			List<Place> cached = cache.get(key);
			if (cached != null) {
				return cached;
			}
		}
		List<Place> places = List.copyOf(delegate.search(query.trim()));
		synchronized (cache) {
			cache.put(key, places);
		}
		return places;
	}

}
