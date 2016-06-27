package org.matsim.contrib.josm.scenario;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.pt.transitSchedule.DepartureImpl;
import org.matsim.pt.transitSchedule.TransitRouteStopImpl;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.utils.objectattributes.ObjectAttributes;
import org.matsim.vehicles.Vehicle;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EditableTransitSchedule implements TransitSchedule {

    final Map<Id<TransitLine>, EditableTransitLine> transitLines = new HashMap<>();
    final Map<Id<TransitStopFacility>, EditableTransitStopFacility> facilities = new HashMap<>();
    final ObjectAttributes transitLinesAttributes = new ObjectAttributes();
    final ObjectAttributes transitStopsAttributes = new ObjectAttributes();
    final TransitScheduleFactory factory = new TransitScheduleFactory() {
        @Override
        public TransitSchedule createTransitSchedule() {
            return new EditableTransitSchedule();
        }

        @Override
        public TransitLine createTransitLine(Id<TransitLine> id) {
            return new EditableTransitLine(id);
        }

        @Override
        public TransitRoute createTransitRoute(Id<TransitRoute> id, NetworkRoute networkRoute, List<TransitRouteStop> list, String s) {
            EditableTransitRoute editableTransitRoute = new EditableTransitRoute(id);
            editableTransitRoute.setRoute(networkRoute);
            editableTransitRoute.getStops().addAll(list);
            editableTransitRoute.setTransportMode(s);
            return editableTransitRoute;
        }

        @Override
        public TransitRouteStop createTransitRouteStop(final TransitStopFacility transitStopFacility, final double arrivalDelay, final double departureDelay) {
            return new EditableTransitRouteStop(transitStopFacility, arrivalDelay, departureDelay);
        }

        @Override
        public TransitStopFacility createTransitStopFacility(Id<TransitStopFacility> id, Coord coord, boolean b) {
            EditableTransitStopFacility editableTransitStopFacility = new EditableTransitStopFacility(id);
            editableTransitStopFacility.setCoord(coord);
            editableTransitStopFacility.setIsBlockingLane(b);
            return editableTransitStopFacility;
        }

        @Override
        public Departure createDeparture(Id<Departure> id, double v) {
            return new EditableDeparture(id, v);
        }
    };

    @Override
    public void addTransitLine(TransitLine transitLine) {
        transitLines.put(transitLine.getId(), ((EditableTransitLine) transitLine));
    }

    @Override
    public boolean removeTransitLine(TransitLine transitLine) {
        EditableTransitLine remove = transitLines.remove(transitLine.getId());
        if (remove != null && remove != transitLine) {
            throw new RuntimeException();
        }
        return remove == transitLine;
    }

    @Override
    public void addStopFacility(TransitStopFacility transitStopFacility) {
        facilities.put(transitStopFacility.getId(), (EditableTransitStopFacility) transitStopFacility);
    }

    @Override
    public Map<Id<TransitLine>, TransitLine> getTransitLines() {
        return Collections.<Id<TransitLine>, TransitLine>unmodifiableMap(transitLines);
    }

    public Map<Id<TransitLine>, EditableTransitLine> getEditableTransitLines() {
        return transitLines;
    }

    @Override
    public Map<Id<TransitStopFacility>, TransitStopFacility> getFacilities() {
        return Collections.<Id<TransitStopFacility>, TransitStopFacility>unmodifiableMap(facilities);
    }

    public Map<Id<TransitStopFacility>, EditableTransitStopFacility> getEditableFacilities() {
        return facilities;
    }

    @Override
    public boolean removeStopFacility(TransitStopFacility transitStopFacility) {
        return this.facilities.remove(transitStopFacility.getId()) != null;
    }

    @Override
    public ObjectAttributes getTransitLinesAttributes() {
        return transitLinesAttributes;
    }

    @Override
    public ObjectAttributes getTransitStopsAttributes() {
        return transitStopsAttributes;
    }

    @Override
    public TransitScheduleFactory getFactory() {
        return factory;
    }
}
