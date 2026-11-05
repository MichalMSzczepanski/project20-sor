package io.github.michalmszczepanski.project20_sor.config;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import io.github.michalmszczepanski.project20_sor.catalog.SorCatalog;
import io.github.michalmszczepanski.project20_sor.queue.QueuePoller;

/** Odświeżanie listy SOR i kolejek w tle; wyłączane w testach przez {@code sor.scheduling.enabled=false}. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnBooleanProperty(name = "sor.scheduling.enabled", matchIfMissing = true)
class SchedulingConfig implements SchedulingConfigurer {

	private final SorCatalog catalog;

	private final QueuePoller poller;

	private final SorProperties properties;

	SchedulingConfig(SorCatalog catalog, QueuePoller poller, SorProperties properties) {
		this.catalog = catalog;
		this.poller = poller;
		this.properties = properties;
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar registrar) {
		registrar.addTriggerTask(catalog::refresh, this::nextCatalogRefresh);
		registrar.addFixedDelayTask(new FixedDelayTask(poller::pollAll, properties.queue().pollInterval(),
				properties.queue().initialDelay()));
	}

	/** Od razu przy starcie; potem rzadko, ale często, dopóki lista jest pusta (np. źródło chwilowo leżało). */
	private Instant nextCatalogRefresh(TriggerContext context) {
		Instant last = context.lastCompletion();
		if (last == null) {
			return context.getClock().instant();
		}
		SorProperties.Catalog config = properties.catalog();
		return last.plus(catalog.units().isEmpty() ? config.retryInterval() : config.refreshInterval());
	}

}
