package org.matsim.contrib.josm;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.josm.scenario.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.LinkImpl;
import org.matsim.core.network.NodeImpl;
import org.matsim.core.population.routes.LinkNetworkRouteImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.TransitStopFacilityImpl;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt.utils.CreatePseudoNetwork;
import org.openstreetmap.josm.Main;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

class TransitScheduleExporter {

	private File scheduleFile;

	public TransitScheduleExporter(File scheduleFile) {
		this.scheduleFile = scheduleFile;
	}

	void run(MATSimLayer layer) {
		Scenario targetScenario = convertIdsAndFilterDeleted(layer.getScenario());

		// if (Main.pref.getBoolean("matsim_transit_lite")) {
		// CreatePseudoNetwork pseudoNetworkCreator = new
		// CreatePseudoNetwork(targetScenario.getTransitSchedule(),
		// targetScenario.getNetwork(), "dummy_");
		// pseudoNetworkCreator.createNetwork();
		// }
		if (targetScenario.getTransitSchedule() != null) {
			new TransitScheduleWriter(targetScenario.getTransitSchedule()).writeFile(scheduleFile.getPath());
		}
	}

	static Scenario convertIdsAndFilterDeleted(EditableScenario layerScenario) {
		Config config = ConfigUtils.createConfig();
		Scenario targetScenario = ScenarioUtils.createScenario(config);
		config.transit().setUseTransit(true);
		if (layerScenario.getTransitSchedule() != null) {
			EditableTransitSchedule oldSchedule = layerScenario.getTransitSchedule();
			TransitSchedule newSchedule = targetScenario.getTransitSchedule();
			for (EditableTransitStopFacility stop : oldSchedule.getEditableFacilities().values()) {
				Id<TransitStopFacility> id = stop.getOrigId();
				TransitStopFacility newStop = newSchedule.getFactory().createTransitStopFacility(id, stop.getCoord(), stop.getIsBlockingLane());
				Id<Link> linkId = stop.getLinkId();
				if (linkId != null) {
					Link oldLink = layerScenario.getNetwork().getLinks().get(linkId);
					Id<Link> newLinkId = Id.createLinkId(((LinkImpl) oldLink).getOrigId());
					newStop.setLinkId(newLinkId);
				}
				Id<Node> nodeId = ((EditableTransitStopFacility) stop).getNodeId();
				if (nodeId != null) {
					Node oldNode = layerScenario.getNetwork().getNodes().get(Id.createNodeId(nodeId));
					for (Link oldInLink : oldNode.getInLinks().values()) {
						Id<Link> newInLinkId = Id.createLinkId(((LinkImpl) oldInLink).getOrigId());
						newStop.setLinkId(newInLinkId); // last one wins
					}
				}
				newSchedule.addStopFacility(newStop);
			}

			for (EditableTransitLine line : layerScenario.getTransitSchedule().getEditableTransitLines().values()) {
				Id<TransitLine> lineId = Id.create(line.getRealId(), TransitLine.class);
				TransitLine newTLine = newSchedule.getFactory().createTransitLine(lineId);
				newSchedule.addTransitLine(newTLine);

				for (EditableTransitRoute route : line.getEditableRoutes().values()) {
					if (!route.isDeleted()) {
						List<Id<Link>> links = new ArrayList<>();
						NetworkRoute networkRoute = route.getRoute();
						NetworkRoute newNetworkRoute;
						if (networkRoute != null) {
							Id<Link> startLinkId = Id.createLinkId(((LinkImpl) layerScenario.getNetwork().getLinks()
									.get(networkRoute.getStartLinkId())).getOrigId());
							for (Id<Link> id : networkRoute.getLinkIds()) {
								links.add(Id.createLinkId(((LinkImpl) layerScenario.getNetwork().getLinks().get(id)).getOrigId()));
							}
							Id<Link> endLinkId = Id.createLinkId(((LinkImpl) layerScenario.getNetwork().getLinks().get(networkRoute.getEndLinkId()))
									.getOrigId());
							newNetworkRoute = new LinkNetworkRouteImpl(startLinkId, endLinkId);
							newNetworkRoute.setLinkIds(startLinkId, links, endLinkId);
						} else {
							newNetworkRoute = null;
						}

						List<TransitRouteStop> newTRStops = new ArrayList<>();
						for (TransitRouteStop tRStop : route.getStops()) {
							Id<TransitStopFacility> stopId = ((EditableTransitStopFacility) tRStop.getStopFacility()).getOrigId();
							TransitRouteStop newTRStop = newSchedule.getFactory().createTransitRouteStop(newSchedule.getFacilities().get(stopId),
									tRStop.getArrivalOffset(), tRStop.getDepartureOffset());
							String awaitDepartureTime = String.valueOf(layerScenario.getTransitSchedule().getTransitStopsAttributes()
									.getAttribute(tRStop.getStopFacility().getName() + "_" + route.getRealId(), "awaitDepartureTime"));
							if (awaitDepartureTime != null) {
								newTRStop.setAwaitDepartureTime(Boolean.parseBoolean(awaitDepartureTime));
							}
							newTRStops.add(newTRStop);
						}

						Id<TransitRoute> routeId = route.getRealId();

						TransitRoute newTRoute = newSchedule.getFactory().createTransitRoute(routeId, newNetworkRoute, newTRStops,
								route.getTransportMode());
						for (Departure departure : route.getDepartures().values()) {
							newTRoute.addDeparture(departure);
						}
						newTLine.addRoute(newTRoute);
					}
				}
				if (newTLine.getRoutes().isEmpty()) {
					newSchedule.removeTransitLine(newTLine);
				}
			}
		}
		return targetScenario;
	}

}
