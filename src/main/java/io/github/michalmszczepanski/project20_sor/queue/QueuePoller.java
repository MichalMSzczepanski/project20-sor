package io.github.michalmszczepanski.project20_sor.queue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.michalmszczepanski.project20_sor.catalog.SorCatalog;
import io.github.michalmszczepanski.project20_sor.config.SorProperties;

/**
 * Jeden przebieg = jedno zapytanie o każdą kolejkę z listy SOR, po kolei i równo rozłożone w czasie
 * ({@code spreadOver}), żeby obciążenie pacjent.gov.pl było stałe, a nie seriami. Kolejki, dla których źródło
 * chwilowo nie miało danych, pytamy jeszcze raz po {@code noDataRetryDelay}.
 * Wyjątek: pierwszy przebieg po starcie idzie {@code startupConcurrency} zapytaniami naraz — inaczej przez
 * kilka minut po restarcie część SOR nie miałaby żadnych danych.
 */
@Component
public class QueuePoller {

	private static final Logger log = LoggerFactory.getLogger(QueuePoller.class);

	@FunctionalInterface
	interface Sleeper {

		void sleep(Duration duration) throws InterruptedException;

	}

	private final SorCatalog catalog;

	private final SorQueueSource source;

	private final QueueStateStore store;

	private final Clock clock;

	private final Sleeper sleeper;

	private final Duration spreadOver;

	private final Duration noDataRetryDelay;

	private final int startupConcurrency;

	private volatile PollSummary lastPoll;

	@Autowired
	public QueuePoller(SorCatalog catalog, SorQueueSource source, QueueStateStore store, Clock clock,
			SorProperties properties) {
		this(catalog, source, store, clock, duration -> Thread.sleep(duration.toMillis()),
				properties.queue().spreadOver(), properties.queue().noDataRetryDelay(),
				properties.queue().startupConcurrency());
	}

	QueuePoller(SorCatalog catalog, SorQueueSource source, QueueStateStore store, Clock clock, Sleeper sleeper,
			Duration spreadOver, Duration noDataRetryDelay, int startupConcurrency) {
		this.catalog = catalog;
		this.source = source;
		this.store = store;
		this.clock = clock;
		this.sleeper = sleeper;
		this.spreadOver = spreadOver;
		this.noDataRetryDelay = noDataRetryDelay;
		this.startupConcurrency = startupConcurrency;
	}

	public void pollAll() {
		List<String> queueIds = catalog.queueIds().stream().sorted().toList();
		if (queueIds.isEmpty()) {
			log.warn("Lista SOR jest pusta — pomijam odpytywanie kolejek");
			return;
		}
		store.retainOnly(catalog.queueIds());
		Instant startedAt = clock.instant();
		boolean startup = lastPoll == null;
		// Ten sam odstęp dla ponowień: kilka ponowień nie może się rozciągać na całe spreadOver.
		Duration gap = spreadOver.dividedBy(queueIds.size());
		try {
			List<String> noData = startup ? pollConcurrently(queueIds) : pollSpread(queueIds, startedAt, gap);
			int retried = noData.size();
			if (!noData.isEmpty()) {
				sleeper.sleep(noDataRetryDelay);
				noData = startup ? pollConcurrently(noData) : pollSpread(noData, clock.instant(), gap);
			}
			int failures = (int) queueIds.stream()
					.filter(id -> store.find(id).map(QueueState::lastError).isPresent())
					.count();
			Instant finishedAt = clock.instant();
			lastPoll = new PollSummary(finishedAt, Duration.between(startedAt, finishedAt), queueIds.size(), failures);
			log.info("Odpytano {} kolejek SOR, błędów: {} (bez danych przy pierwszej próbie: {}, nadal: {})",
					queueIds.size(), failures, retried, noData.size());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			log.info("Przerwano odpytywanie kolejek SOR");
		}
	}

	/**
	 * Zapytanie i-te startuje w chwili {@code start + i × odstęp} — czas samych zapytań nie wydłuża przebiegu.
	 * @return kolejki, dla których źródło nie miało teraz danych
	 */
	private List<String> pollSpread(List<String> queueIds, Instant start, Duration gap) throws InterruptedException {
		List<String> noData = new ArrayList<>();
		for (int i = 0; i < queueIds.size(); i++) {
			Duration wait = Duration.between(clock.instant(), start.plus(gap.multipliedBy(i)));
			if (!wait.isNegative() && !wait.isZero()) {
				sleeper.sleep(wait);
			}
			if (!pollOne(queueIds.get(i))) {
				noData.add(queueIds.get(i));
			}
		}
		return noData;
	}

	/** Pierwszy przebieg po starcie: kilka zapytań naraz, bez rozkładania w czasie. */
	private List<String> pollConcurrently(List<String> queueIds) throws InterruptedException {
		ExecutorService executor = Executors.newFixedThreadPool(startupConcurrency);
		try {
			List<Future<Boolean>> results = executor.invokeAll(
					queueIds.stream().<Callable<Boolean>>map(id -> () -> pollOne(id)).toList());
			List<String> noData = new ArrayList<>();
			for (int i = 0; i < queueIds.size(); i++) {
				if (!results.get(i).get()) {
					noData.add(queueIds.get(i));
				}
			}
			return noData;
		}
		catch (ExecutionException ex) {
			throw new IllegalStateException("Nieoczekiwany błąd odpytywania kolejek", ex);
		}
		finally {
			executor.shutdownNow();
		}
	}

	/** @return {@code false}, gdy źródło chwilowo nie miało danych (warto ponowić) */
	private boolean pollOne(String queueId) {
		try {
			store.recordSnapshot(source.fetch(queueId));
		}
		catch (QueueNoDataException ex) {
			record(queueId, ex);
			return false;
		}
		catch (QueueFetchException ex) {
			record(queueId, ex);
		}
		return true;
	}

	private void record(String queueId, QueueFetchException ex) {
		log.debug("Kolejka {}: {}", queueId, ex.getMessage());
		store.recordFailure(queueId, ex.getMessage(), clock.instant());
	}

	public Optional<PollSummary> lastPoll() {
		return Optional.ofNullable(lastPoll);
	}

	public record PollSummary(Instant finishedAt, Duration took, int queues, int failures) {
	}

}
