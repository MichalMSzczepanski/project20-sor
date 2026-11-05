package io.github.michalmszczepanski.project20_sor.ranking;

import io.github.michalmszczepanski.project20_sor.queue.QueueStage;

/** Priorytet nadawany na triażu; każdy ma w TOPSOR osobną kolejkę. */
public enum TriagePriority {

	HIGHEST(QueueStage.PRIORITY_HIGHEST),
	HIGH(QueueStage.PRIORITY_HIGH),
	MEDIUM(QueueStage.PRIORITY_MEDIUM),
	LOW(QueueStage.PRIORITY_LOW),
	LOWEST(QueueStage.PRIORITY_LOWEST);

	private final QueueStage stage;

	TriagePriority(QueueStage stage) {
		this.stage = stage;
	}

	public QueueStage stage() {
		return stage;
	}

}
