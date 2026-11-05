package io.github.michalmszczepanski.project20_sor.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Zegar przesuwany ręcznie — np. przez atrapę „uśpienia” w testach kodu, który czeka. */
public final class ManualClock extends Clock {

	private Instant now;

	public ManualClock(Instant start) {
		this.now = start;
	}

	public synchronized void advance(Duration duration) {
		now = now.plus(duration);
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}

	@Override
	public synchronized Instant instant() {
		return now;
	}

}
