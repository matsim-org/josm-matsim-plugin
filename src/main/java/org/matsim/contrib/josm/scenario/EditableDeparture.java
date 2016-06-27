package org.matsim.contrib.josm.scenario;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.vehicles.Vehicle;

public class EditableDeparture implements Departure {

	public EditableDeparture(Id<Departure> id, double departureTime) {
		this.departureTime = departureTime;
		this.id = id;
	}

	private double departureTime;
	private Id<Vehicle> vehicleId;
	private Id<Departure> id;

	@Override
	public double getDepartureTime() {
		return departureTime;
	}

	@Override
	public void setVehicleId(Id<Vehicle> vehicleId) {
		this.vehicleId = vehicleId;
	}

	@Override
	public Id<Vehicle> getVehicleId() {
		return this.vehicleId;
	}

	@Override
	public Id<Departure> getId() {
		return this.id;
	}
}
