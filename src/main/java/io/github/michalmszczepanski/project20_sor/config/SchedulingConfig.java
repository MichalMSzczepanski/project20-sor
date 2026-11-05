package io.github.michalmszczepanski.project20_sor.config;

import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedRateTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import io.github.michalmszczepanski.project20_sor.catalog.SorCatalog;
import io.github.michalmszczepanski.project20_sor.catalog.SorTypeRegistry;
import io.github.michalmszczepanski.project20_sor.queue.QueuePoller;

/** Odświeżanie danych w tle; wyłączane w testach przez {@code sor.scheduling.enabled=false}. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnBooleanProperty(name = "sor.scheduling.enabled", matchIfMissing = true)
class SchedulingConfig implements SchedulingConfigurer {

	private final SorCatalog catalog;

	private final SorTypeRegistry sorTypes;

	private final QueuePoller poller;

	private final SorProperties properties;

	SchedulingConfig(SorCatalog catalog, SorTypeRegistry sorTypes, QueuePoller poller, SorProperties properties) {
		this.catalog = catalog;
		this.sorTypes = sorTypes;
		this.poller = poller;
		this.properties = properties;
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar registrar) {
		registrar.addTriggerTask(catalog::refresh, refreshTrigger(catalog::isLoaded,
				properties.catalog().refreshInterval(), properties.catalog().retryInterval()));
		registrar.addTriggerTask(sorTypes::refresh, refreshTrigger(sorTypes::isLoaded,
				properties.rpwdl().refreshInterval(), properties.rpwdl().retryInterval()));
		// Stała częstotliwość, nie stała przerwa: przebieg sam trwa ~spreadOver, więc przerwa po nim wydłużałaby cykl.
		registrar.addFixedRateTask(new FixedRateTask(poller::pollAll, properties.queue().pollInterval(),
				properties.queue().initialDelay()));
	}

	/** Od razu przy starcie; potem rzadko, ale często, dopóki nic nie wczytano (np. źródło chwilowo leżało). */
	static Trigger refreshTrigger(BooleanSupplier loaded, Duration interval, Duration retryInterval) {
		return context -> {
			Instant last = context.lastCompletion();
			if (last == null) {
				return context.getClock().instant();
			}
			return last.plus(loaded.getAsBoolean() ? interval : retryInterval);
		};
	}

}
