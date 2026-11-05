package io.github.michalmszczepanski.project20_sor.queue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import io.github.michalmszczepanski.project20_sor.catalog.SorCatalog;
import io.github.michalmszczepanski.project20_sor.config.SorProperties;

/** Jeden przebieg = jedno zapytanie o każdą kolejkę z listy SOR, najwyżej {@code concurrency} naraz. */
@Component
public class QueuePoller implements DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(QueuePoller.class);

	private final SorCatalog catalog;

	private final SorQueueSource source;

	private final QueueStateStore store;

	private final Clock clock;

	private final ExecutorService executor;

	private volatile PollSummary lastPoll;

	public QueuePoller(SorCatalog catalog, SorQueueSource source, QueueStateStore store, Clock clock,
			SorProperties properties) {
		this.catalog = catalog;
		this.source = source;
		this.store = store;
		this.clock = clock;
		this.executor = Executors.newFixedThreadPool(properties.queue().concurrency());
	}

	public void pollAll() {
		Set<String> queueIds = catalog.queueIds();
		if (queueIds.isEmpty()) {
			log.warn("Lista SOR jest pusta — pomijam odpytywanie kolejek");
			return;
		}
		store.retainOnly(queueIds);
		Instant startedAt = clock.instant();
		AtomicInteger failures = new AtomicInteger();
		List<Callable<Void>> tasks = queueIds.stream().<Callable<Void>>map(id -> () -> {
			if (!pollOne(id)) {
				failures.incrementAndGet();
			}
			return null;
		}).toList();
		try {
			executor.invokeAll(tasks);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return;
		}
		Instant finishedAt = clock.instant();
		lastPoll = new PollSummary(finishedAt, Duration.between(startedAt, finishedAt), queueIds.size(), failures.get());
		log.info("Odpytano {} kolejek SOR, błędów: {}", queueIds.size(), failures.get());
	}

	public Optional<PollSummary> lastPoll() {
		return Optional.ofNullable(lastPoll);
	}

	public record PollSummary(Instant finishedAt, Duration took, int queues, int failures) {
	}

	private boolean pollOne(String queueId) {
		try {
			store.recordSnapshot(source.fetch(queueId));
			return true;
		}
		catch (QueueFetchException ex) {
			log.debug("Kolejka {}: {}", queueId, ex.getMessage());
			store.recordFailure(queueId, ex.getMessage(), clock.instant());
			return false;
		}
	}

	@Override
	public void destroy() {
		executor.shutdownNow();
	}

}
