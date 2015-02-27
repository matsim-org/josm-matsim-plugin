package org.matsim.contrib.josm.scenario;

import org.matsim.api.core.v01.Id;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EditableTransitRoute implements TransitRoute {

    final Id<TransitRoute> id;
    final List<TransitRouteStop> stops = new ArrayList<>();
    final Map<Id<Departure>, Departure> departures = new HashMap<>();

    Id<TransitRoute> realId;
    String description;
    String transportMode;
    NetworkRoute route;
    boolean isDeleted = false;

    public EditableTransitRoute(Id<TransitRoute> id) {
        this.id = id;
    }

    @Override
    public Id<TransitRoute> getId() {
        return id;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String getTransportMode() {
        return transportMode;
    }

    @Override
    public void addDeparture(Departure departure) {
        this.departures.put(departure.getId(), departure);
    }

    @Override
    public boolean removeDeparture(Departure departure) {
        return null != this.departures.remove(departure.getId());
    }

    @Override
    public void setTransportMode(String transportMode) {
        this.transportMode = transportMode;
    }

    @Override
    public Map<Id<Departure>, Departure> getDepartures() {
        return departures;
    }

    public NetworkRoute getRoute() {
        return route;
    }

    public void setRoute(NetworkRoute route) {
        this.route = route;
    }

    @Override
    public List<TransitRouteStop> getStops() {
        return stops;
    }

    @Override
    public TransitRouteStop getStop(TransitStopFacility transitStopFacility) {
        for (TransitRouteStop stop : stops) {
            if (stop.getStopFacility() == transitStopFacility) {
                return stop;
            }
        }
        return null;
    }

    public Id<TransitRoute> getRealId() {
        return realId;
    }

    public void setRealId(Id<TransitRoute> realId) {
        this.realId = realId;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

}
