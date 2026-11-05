package io.github.michalmszczepanski.project20_sor.queue;

public interface SorQueueSource {

	/** @throws QueueFetchException gdy nie da się pobrać lub zrozumieć stanu kolejki */
	QueueSnapshot fetch(String queueId);

}
