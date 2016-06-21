package org.matsim.contrib.josm;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.josm.scenario.*;
import org.matsim.core.config.Config;
import org.matsim.core.network.LinkImpl;
import org.matsim.core.network.NetworkWriter;
import org.matsim.core.network.NodeImpl;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.population.routes.LinkNetworkRouteImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.pt.transitSchedule.api.*;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The task which which writes out the network xml file
 *
 * @author Nico
 *
 */

class ExportTask {

	private final File networkFile;
	private OsmDataLayer layer;

	/**
	 * Creates a new Export task with the given export <code>file</code>
	 * location
	 *
	 * @param file
	 *            The file to be exported to
	 */
	public ExportTask(File file, OsmDataLayer layer) {
		this.networkFile = file;
		this.layer = layer;
	}

	protected void realRun() {
		EditableScenario layerScenario = ((MATSimLayer) layer).getScenario();
		Scenario targetScenario = convertIdsAndFilterDeleted(layerScenario);

		if (Main.pref.getBoolean("matsim_cleanNetwork")) {
			new NetworkCleaner().run(targetScenario.getNetwork());
		}
		new NetworkWriter(targetScenario.getNetwork()).write(networkFile.getPath());
	}

	static EditableScenario convertIdsAndFilterDeleted(EditableScenario layerScenario) {
		Config config = layerScenario.getConfig();
		config.transit().setUseTransit(true);
		EditableScenario targetScenario = EditableScenarioUtils.createScenario(config);

		// copy nodes with switched id fields
		for (Node node : layerScenario.getNetwork().getNodes().values()) {
			Node newNode = targetScenario.getNetwork().getFactory().createNode(Id.create(((NodeImpl) node).getOrigId(), Node.class), node.getCoord());
			targetScenario.getNetwork().addNode(newNode);
		}
		// copy links with switched id fields
		for (Link link : layerScenario.getNetwork().getLinks().values()) {
			Link newLink = targetScenario
					.getNetwork()
					.getFactory()
					.createLink(Id.create(((LinkImpl) link).getOrigId(), Link.class),
							targetScenario.getNetwork().getNodes().get(Id.create(((NodeImpl) link.getFromNode()).getOrigId(), Node.class)),
							targetScenario.getNetwork().getNodes().get(Id.create(((NodeImpl) link.getToNode()).getOrigId(), Node.class)));
			newLink.setFreespeed(link.getFreespeed());
			newLink.setCapacity(link.getCapacity());
			newLink.setLength(link.getLength());
			newLink.setNumberOfLanes(link.getNumberOfLanes());
			newLink.setAllowedModes(link.getAllowedModes());
			targetScenario.getNetwork().addLink(newLink);
		}
		TransitSchedule newSchedule = targetScenario.getTransitSchedule();
		for (EditableTransitStopFacility stop : layerScenario.getTransitSchedule().getEditableFacilities().values()) {
			Id<TransitStopFacility> id = stop.getOrigId();
			TransitStopFacility newStop = newSchedule.getFactory().createTransitStopFacility(id, stop.getCoord(), stop.getIsBlockingLane());
			Id<Link> linkId = stop.getLinkId();
			if (linkId != null) {
				Link oldLink = layerScenario.getNetwork().getLinks().get(linkId);
				Id<Link> newLinkId = Id.createLinkId(((LinkImpl) oldLink).getOrigId());
				newStop.setLinkId(newLinkId);
			}
			Id<Node> nodeId = stop.getNodeId();
			if (nodeId != null) {
				Node oldNode = layerScenario.getNetwork().getNodes().get(Id.createNodeId(nodeId));
				if (oldNode == null) {
					throw new RuntimeException("Stop references a node which is not in the scenario: "+nodeId);
				}
				if (oldNode.getInLinks().size() == 1) {
					Link oldInLink = oldNode.getInLinks().values().iterator().next();
					Id<Link> newInLinkId = Id.createLinkId(((LinkImpl) oldInLink).getOrigId());
					newStop.setLinkId(newInLinkId);
				} // otherwise: ambiguous
			}
			newStop.setName(stop.getName());
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
		return targetScenario;
	}
}