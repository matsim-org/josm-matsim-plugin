package org.matsim.contrib.josm.scenario;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.utils.objectattributes.ObjectAttributes;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EditableTransitSchedule implements TransitSchedule {

    final Map<Id<TransitLine>, EditableTransitLine> transitLines = new HashMap<>();
    final Map<Id<TransitStopFacility>, TransitStopFacility> facilities = new HashMap<>();
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
            return new TransitRouteStop() {

                @Override
                public TransitStopFacility getStopFacility() {
                    return transitStopFacility;
                }

                @Override
                public void setStopFacility(TransitStopFacility transitStopFacility) {
                    throw new RuntimeException();
                }

                @Override
                public double getDepartureOffset() {
                    return departureDelay;
                }

                @Override
                public double getArrivalOffset() {
                    return arrivalDelay;
                }

                @Override
                public void setAwaitDepartureTime(boolean b) {
                    throw new RuntimeException();
                }

                @Override
                public boolean isAwaitDepartureTime() {
                    return false;
                }
            };
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
            return null;
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
        facilities.put(transitStopFacility.getId(), transitStopFacility);
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
