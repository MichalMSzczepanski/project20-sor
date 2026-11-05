package io.github.michalmszczepanski.project20_sor.geo;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.michalmszczepanski.project20_sor.config.SorProperties;

/** Zgrubny szacunek: linia prosta × współczynnik objazdu, przy stałej średniej prędkości. */
@Component
public class StraightLineTravelEstimator implements TravelEstimator {

	private final double detourFactor;

	private final double averageSpeedKmh;

	@Autowired
	public StraightLineTravelEstimator(SorProperties properties) {
		this(properties.travel().detourFactor(), properties.travel().averageSpeedKmh());
	}

	public StraightLineTravelEstimator(double detourFactor, double averageSpeedKmh) {
		if (detourFactor < 1 || averageSpeedKmh <= 0) {
			throw new IllegalArgumentException("detourFactor >= 1 i averageSpeedKmh > 0");
		}
		this.detourFactor = detourFactor;
		this.averageSpeedKmh = averageSpeedKmh;
	}

	@Override
	public List<TravelEstimate> estimate(GeoPoint origin, List<GeoPoint> destinations) {
		return destinations.stream().map(to -> estimate(origin, to)).toList();
	}

	public TravelEstimate estimate(GeoPoint from, GeoPoint to) {
		double distanceKm = from.distanceKmTo(to);
		int minutes = (int) Math.ceil(distanceKm * detourFactor / averageSpeedKmh * 60);
		return new TravelEstimate(distanceKm, minutes, TravelEstimate.Method.STRAIGHT_LINE);
	}

}
