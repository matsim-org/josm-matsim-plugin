package org.matsim.contrib.josm;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.LinkImpl;
import org.matsim.core.population.routes.LinkNetworkRouteImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.*;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.Relation;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

class TransitScheduleExporter {

    private File scheduleFile;

    public TransitScheduleExporter(File scheduleFile) {
        this.scheduleFile = scheduleFile;
    }

    void run(MATSimLayer layer) {
        Config config = ConfigUtils.createConfig();
        Scenario sc = ScenarioUtils.createScenario(config);
        config.scenario().setUseTransit(true);
        config.scenario().setUseVehicles(true);
        TransitSchedule schedule = sc.getTransitSchedule();
        if (layer.getMatsimScenario().getTransitSchedule() != null) {
            TransitSchedule oldSchedule = layer
                    .getMatsimScenario().getTransitSchedule();

            for (TransitStopFacility stop : oldSchedule.getFacilities()
                    .values()) {
            	Id<TransitStopFacility> id;
            	Relation stopRelation = (Relation) layer.data.getPrimitiveById(Long.parseLong(stop.getId().toString()), OsmPrimitiveType.RELATION);
            	if (stopRelation.hasKey("id")) {
            		id = Id.create(stopRelation.get("id"), TransitStopFacility.class);
            	} else {
            		id = stop.getId();
            	}
                TransitStopFacility newStop = schedule.getFactory()
                        .createTransitStopFacility(
                                id, stop.getCoord(),
                                stop.getIsBlockingLane());

                Id<Link> linkId = stop.getLinkId();
                if (linkId != null) {
                    Link oldLink = layer.getMatsimScenario().getNetwork().getLinks().get(linkId);
                    Id<Link> newLinkId = Id.createLinkId(((LinkImpl) oldLink).getOrigId());
                    newStop.setLinkId(newLinkId);
                }
                schedule.addStopFacility(newStop);
            }

            for (TransitLine line : layer
                    .getMatsimScenario().getTransitSchedule()
                    .getTransitLines().values()) {

                Id<TransitLine> lineId;
                Relation lineRelation = (Relation) layer.data.getPrimitiveById(Long.parseLong(line.getId().toString()), OsmPrimitiveType.RELATION);
                
                if (lineRelation.hasKey("ref")) {
                	lineId = Id.create(lineRelation.get("ref"), TransitLine.class);
                } else {
                	lineId = line.getId();
                }
                TransitLine newTLine = schedule.getFactory().createTransitLine(
                            lineId);
                    schedule.addTransitLine(newTLine);
               

                for (TransitRoute route : line.getRoutes().values()) {
                    List<Id<Link>> links = new ArrayList<>();
                    Id<Link> startLinkId = Id
                            .createLinkId(((LinkImpl) layer
                                    .getMatsimScenario().getNetwork()
                                    .getLinks()
                                    .get(route.getRoute().getStartLinkId()))
                                    .getOrigId());
                    for (Id<Link> id : route.getRoute().getLinkIds()) {
                        links.add(Id
                                .createLinkId(((LinkImpl) layer
                                        .getMatsimScenario().getNetwork()
                                        .getLinks().get(id)).getOrigId()));
                    }
                    Id<Link> endLinkId = Id
                            .createLinkId(((LinkImpl) layer
                                    .getMatsimScenario().getNetwork()
                                    .getLinks()
                                    .get(route.getRoute().getEndLinkId()))
                                    .getOrigId());
                    NetworkRoute networkRoute = new LinkNetworkRouteImpl(
                            startLinkId, endLinkId);
                    networkRoute.setLinkIds(startLinkId, links, endLinkId);

                    List<TransitRouteStop> newTRStops = new ArrayList<>();
                    for (TransitRouteStop tRStop : route.getStops()) {
                    	Id<TransitStopFacility> stopId;
                    	Relation stopRelation = (Relation) layer.data.getPrimitiveById(Long.parseLong(tRStop.getStopFacility().getId().toString()), OsmPrimitiveType.RELATION);
                    	if (stopRelation.hasKey("id")) {
                    		stopId = Id.create(stopRelation.get("id"), TransitStopFacility.class);
                    	} else {
                    		stopId = tRStop.getStopFacility().getId();
                    	}
                        newTRStops.add(schedule.getFactory()
                                .createTransitRouteStop(
                                        schedule.getFacilities().get(stopId), tRStop.getArrivalOffset(), tRStop.getDepartureOffset()));
                    }
                    
                    Id<TransitRoute> routeId;
                    Relation routeRelation = (Relation) layer.data.getPrimitiveById(Long.parseLong(route.getId().toString()), OsmPrimitiveType.RELATION);
                    if (routeRelation.hasKey("ref")) {
                    	routeId = Id.create(routeRelation.get("ref"), TransitRoute.class);
                    } else {
                    	routeId = route.getId();
                    }

                    TransitRoute newTRoute = schedule.getFactory()
                            .createTransitRoute(routeId,
                                    networkRoute, newTRStops,
                                    route.getTransportMode());
                    newTLine.addRoute(newTRoute);
                }
            }
            new TransitScheduleWriter(schedule).writeFile(scheduleFile.getPath());
        }

    }

}
