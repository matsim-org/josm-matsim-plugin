package org.matsim.contrib.josm;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.josm.scenario.EditableScenario;
import org.matsim.contrib.josm.scenario.EditableTransitLine;
import org.matsim.contrib.josm.scenario.EditableTransitRoute;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.LinkImpl;
import org.matsim.core.population.routes.LinkNetworkRouteImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.TransitRouteImpl;
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
        Scenario targetScenario = convertIds(layer.getScenario());
        if (targetScenario.getTransitSchedule() != null) {
            new TransitScheduleWriter(targetScenario.getTransitSchedule()).writeFile(scheduleFile.getPath());
        }
    }

    static Scenario convertIds(EditableScenario layerScenario) {
        Config config = ConfigUtils.createConfig();
        Scenario targetScenario = ScenarioUtils.createScenario(config);
        config.scenario().setUseTransit(true);
        config.scenario().setUseVehicles(true);
        if (layerScenario.getTransitSchedule() != null) {
            TransitSchedule oldSchedule = layerScenario.getTransitSchedule();
            TransitSchedule newSchedule = targetScenario.getTransitSchedule();
            for (TransitStopFacility stop : oldSchedule.getFacilities()
                    .values()) {
            	Id<TransitStopFacility> id = Id.create(stop.getName(), TransitStopFacility.class);
                TransitStopFacility newStop = newSchedule.getFactory()
                        .createTransitStopFacility(
                                id, stop.getCoord(),
                                stop.getIsBlockingLane());

                Id<Link> linkId = stop.getLinkId();
                if (linkId != null) {
                    Link oldLink = layerScenario.getNetwork().getLinks().get(linkId);
                    Id<Link> newLinkId = Id.createLinkId(((LinkImpl) oldLink).getOrigId());
                    newStop.setLinkId(newLinkId);
                }
                newSchedule.addStopFacility(newStop);
            }

            for (EditableTransitLine line : layerScenario.getTransitSchedule()
                    .getEditableTransitLines().values()) {

                Id<TransitLine> lineId = Id.create(line.getRealId(), TransitLine.class);
                TransitLine newTLine = newSchedule.getFactory().createTransitLine(
                        lineId);
                    newSchedule.addTransitLine(newTLine);


                for (EditableTransitRoute route : line.getEditableRoutes().values()) {
                    List<Id<Link>> links = new ArrayList<>();
                    NetworkRoute networkRoute = route.getRoute();
                    NetworkRoute newNetworkRoute;
                    if (networkRoute != null) {
                        Id<Link> startLinkId = Id
                                .createLinkId(((LinkImpl) layerScenario.getNetwork()
                                        .getLinks()
                                        .get(networkRoute.getStartLinkId()))
                                        .getOrigId());
                        for (Id<Link> id : networkRoute.getLinkIds()) {
                            links.add(Id
                                    .createLinkId(((LinkImpl) layerScenario.getNetwork()
                                            .getLinks().get(id)).getOrigId()));
                        }
                        Id<Link> endLinkId = Id
                                .createLinkId(((LinkImpl) layerScenario.getNetwork()
                                        .getLinks()
                                        .get(networkRoute.getEndLinkId()))
                                        .getOrigId());
                        newNetworkRoute = new LinkNetworkRouteImpl(startLinkId, endLinkId);
                        newNetworkRoute.setLinkIds(startLinkId, links, endLinkId);
                    } else {
                        newNetworkRoute = null;
                    }

                    List<TransitRouteStop> newTRStops = new ArrayList<>();
                    for (TransitRouteStop tRStop : route.getStops()) {
                    	Id<TransitStopFacility> stopId = Id.create(tRStop.getStopFacility().getName(), TransitStopFacility.class);

                        newTRStops.add(newSchedule.getFactory()
                                .createTransitRouteStop(
                                        newSchedule.getFacilities().get(stopId), tRStop.getArrivalOffset(), tRStop.getDepartureOffset()));
                    }

                    Id<TransitRoute> routeId = route.getRealId();

                    TransitRoute newTRoute = newSchedule.getFactory()
                            .createTransitRoute(routeId,
                                    newNetworkRoute, newTRStops,
                                    route.getTransportMode());
                    newTLine.addRoute(newTRoute);
                }
            }
        }
        return targetScenario;
    }

}
