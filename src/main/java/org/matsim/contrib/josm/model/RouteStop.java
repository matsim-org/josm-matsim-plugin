package org.matsim.contrib.josm.model;

import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class RouteStop {
	private boolean awaitDepartureTime;
	private double departureOffset;
	private double arrivalOffset;
	private StopArea stopFacility;

	public RouteStop(StopArea stopFacility, double arrivalOffset, double departureOffset) {
		this.stopFacility = stopFacility;
		this.arrivalOffset = arrivalOffset;
		this.departureOffset = departureOffset;
	}

	public StopArea getStopArea() {
		return stopFacility;
	}

	public void setStopArea(StopArea stopFacility) {
		this.stopFacility = stopFacility;
	}

	public double getDepartureOffset() {
		return departureOffset;
	}

	public double getArrivalOffset() {
		return arrivalOffset;
	}

	public void setAwaitDepartureTime(boolean awaitDepartureTime) {
		this.awaitDepartureTime = awaitDepartureTime;
	}

	public boolean isAwaitDepartureTime() {
		return this.awaitDepartureTime;
	}
}
