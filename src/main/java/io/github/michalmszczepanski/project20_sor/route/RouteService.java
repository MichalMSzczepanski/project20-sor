package io.github.michalmszczepanski.project20_sor.route;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import io.github.michalmszczepanski.project20_sor.geo.GeoPoint;

@Service
public class RouteService {

	private final Map<RouteMode, RoutePlanner> planners = new EnumMap<>(RouteMode.class);

	public RouteService(List<RoutePlanner> planners) {
		planners.forEach(planner -> this.planners.put(planner.mode(), planner));
	}

	/** @throws RoutingException gdy usługa nie odpowiada albo dla trybu jej nie skonfigurowano (np. wyłączony OSRM) */
	public Optional<Route> plan(GeoPoint from, GeoPoint to, RouteMode mode) {
		RoutePlanner planner = planners.get(mode);
		if (planner == null) {
			throw new RoutingException("Trasy w trybie " + mode + " są wyłączone", null);
		}
		return planner.plan(from, to);
	}

}
