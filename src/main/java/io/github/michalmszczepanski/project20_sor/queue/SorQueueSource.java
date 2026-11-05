package io.github.michalmszczepanski.project20_sor.queue;

@FunctionalInterface
public interface SorQueueSource {

	/** @throws QueueFetchException gdy nie da się pobrać lub zrozumieć stanu kolejki */
	QueueSnapshot fetch(String queueId);

}
