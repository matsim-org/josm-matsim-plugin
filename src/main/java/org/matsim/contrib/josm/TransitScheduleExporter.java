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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TransitScheduleExporter {

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

                TransitStopFacility newStop = schedule.getFactory()
                        .createTransitStopFacility(
                                stop.getId(), stop.getCoord(),
                                stop.getIsBlockingLane());

                Id<Link> oldId = stop.getLinkId();
                Link oldLink = layer.getMatsimScenario()
                        .getNetwork().getLinks().get(oldId);
                Id<Link> newLinkId = Id.createLinkId(((LinkImpl) oldLink)
                        .getOrigId());
                newStop.setLinkId(newLinkId);
                schedule.addStopFacility(newStop);
            }

            for (TransitLine line : layer
                    .getMatsimScenario().getTransitSchedule()
                    .getTransitLines().values()) {

                TransitLine newTLine;
                if (schedule.getTransitLines().containsKey(line.getId())) {
                    newTLine = schedule.getTransitLines().get(line.getId());
                } else {
                    newTLine = schedule.getFactory().createTransitLine(
                            line.getId());
                    schedule.addTransitLine(newTLine);
                }

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
                        newTRStops.add(schedule.getFactory()
                                .createTransitRouteStop(
                                        schedule.getFacilities().get(tRStop.getStopFacility().getId()), tRStop.getArrivalOffset(), tRStop.getDepartureOffset()));
                    }

                    TransitRoute newTRoute = schedule.getFactory()
                            .createTransitRoute(route.getId(),
                                    networkRoute, newTRStops,
                                    route.getTransportMode());
                    newTLine.addRoute(newTRoute);
                }
            }
            new TransitScheduleWriter(schedule).writeFile(scheduleFile.getPath());
        }

    }

}
