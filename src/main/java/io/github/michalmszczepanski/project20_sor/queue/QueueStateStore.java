package io.github.michalmszczepanski.project20_sor.queue;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/** Stan kolejek w pamięci. Błąd nie kasuje ostatniego poprawnego pomiaru — o świeżości decyduje czytający. */
@Component
public class QueueStateStore {

	private static final QueueState EMPTY = new QueueState(null, null, null);

	private final Map<String, QueueState> states = new ConcurrentHashMap<>();

	public void recordSnapshot(QueueSnapshot snapshot) {
		states.merge(snapshot.queueId(), EMPTY.withSnapshot(snapshot), (old, fresh) -> old.withSnapshot(snapshot));
	}

	public void recordFailure(String queueId, String error, Instant at) {
		states.compute(queueId, (id, old) -> (old == null ? EMPTY : old).withError(error, at));
	}

	public Optional<QueueState> find(String queueId) {
		return Optional.ofNullable(states.get(queueId));
	}

	/** Usuwa kolejki, których nie ma już na liście SOR. */
	public void retainOnly(Set<String> queueIds) {
		states.keySet().retainAll(queueIds);
	}

}
