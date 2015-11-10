package org.matsim.contrib.josm.scenario;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class EditableTransitRouteStop implements TransitRouteStop {
	private boolean awaitDepartureTime;
	private double departureOffset;
	private double arrivalOffset;
	private TransitStopFacility stopFacility;

	public EditableTransitRouteStop(TransitStopFacility stopFacility, double arrivalOffset, double departureOffset) {
		this.stopFacility = stopFacility;
		this.arrivalOffset = arrivalOffset;
		this.departureOffset = departureOffset;
	}

	@Override
	public TransitStopFacility getStopFacility() {
		return stopFacility;
	}

	@Override
	public void setStopFacility(TransitStopFacility stopFacility) {
		this.stopFacility = stopFacility;
	}

	@Override
	public double getDepartureOffset() {
		return departureOffset;
	}

	@Override
	public double getArrivalOffset() {
		return arrivalOffset;
	}

	@Override
	public void setAwaitDepartureTime(boolean awaitDepartureTime) {
		this.awaitDepartureTime = awaitDepartureTime;
	}

	@Override
	public boolean isAwaitDepartureTime() {
		return this.awaitDepartureTime;
	}
}
