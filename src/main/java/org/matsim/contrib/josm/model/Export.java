package org.matsim.contrib.josm.model;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.josm.scenario.*;
import org.matsim.core.config.Config;
import org.matsim.core.network.LinkImpl;
import org.matsim.core.network.NodeImpl;
import org.matsim.core.population.routes.LinkNetworkRouteImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.pt.transitSchedule.api.*;

import java.util.*;
import java.util.stream.Collectors;

public class Export {

	public static EditableScenario convertIdsAndFilterDeleted(NetworkModel networkModel) {
		EditableScenario scenario = networkModel.getScenario();
		fixTransitSchedule(scenario);
		Config config = scenario.getConfig();
		config.transit().setUseTransit(true);
		EditableScenario targetScenario = EditableScenarioUtils.createScenario(config);

		// copy nodes with switched id fields
		for (Node node : scenario.getNetwork().getNodes().values()) {
			Node newNode = targetScenario.getNetwork().getFactory().createNode(Id.create(((NodeImpl) node).getOrigId(), Node.class), node.getCoord());
			targetScenario.getNetwork().addNode(newNode);
		}
		// copy links with switched id fields
		for (Link link : scenario.getNetwork().getLinks().values()) {
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
		for (EditableTransitStopFacility stop : scenario.getTransitSchedule().getEditableFacilities().values()) {
			Id<TransitStopFacility> id = stop.getOrigId();
			TransitStopFacility newStop = newSchedule.getFactory().createTransitStopFacility(id, stop.getCoord(), stop.getIsBlockingLane());
			Id<Link> linkId = stop.getLinkId();
			if (linkId != null) {
				Link oldLink = scenario.getNetwork().getLinks().get(linkId);
				Id<Link> newLinkId = Id.createLinkId(((LinkImpl) oldLink).getOrigId());
				newStop.setLinkId(newLinkId);
			}
			Id<Node> nodeId = stop.getNodeId();
			if (nodeId != null) {
				Node oldNode = scenario.getNetwork().getNodes().get(Id.createNodeId(nodeId));
				if (oldNode == null) {
					throw new RuntimeException("Stop references a node which is not in the scenario: "+nodeId);
				}
				if (newStop.getLinkId() == null) {
					for (Link oldInLink : oldNode.getInLinks().values()) {
						Id<Link> newInLinkId = Id.createLinkId(((LinkImpl) oldInLink).getOrigId());
						newStop.setLinkId(newInLinkId); // last one wins
					}
				}
			}
			newStop.setName(stop.getName());
			newSchedule.addStopFacility(newStop);
		}

		for (EditableTransitLine line : scenario.getTransitSchedule().getEditableTransitLines().values()) {
			Id<TransitLine> lineId = Id.create(line.getRealId(), TransitLine.class);
			TransitLine newTLine = newSchedule.getFactory().createTransitLine(lineId);
			newSchedule.addTransitLine(newTLine);

			for (EditableTransitRoute route : line.getEditableRoutes().values()) {
				if (!route.isDeleted()) {
					List<Id<Link>> links = new ArrayList<>();
					NetworkRoute networkRoute = route.getRoute();
					NetworkRoute newNetworkRoute;
					if (networkRoute != null) {
						Id<Link> startLinkId = Id.createLinkId(((LinkImpl) scenario.getNetwork().getLinks()
								.get(networkRoute.getStartLinkId())).getOrigId());
						for (Id<Link> id : networkRoute.getLinkIds()) {
							links.add(Id.createLinkId(((LinkImpl) scenario.getNetwork().getLinks().get(id)).getOrigId()));
						}
						Id<Link> endLinkId = Id.createLinkId(((LinkImpl) scenario.getNetwork().getLinks().get(networkRoute.getEndLinkId()))
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
						String awaitDepartureTime = String.valueOf(scenario.getTransitSchedule().getTransitStopsAttributes()
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
		splitTransitStopFacilities(targetScenario);
		return targetScenario;
	}

	private static void fixTransitSchedule(EditableScenario sourceScenario) {
		for (TransitLine transitLine : sourceScenario.getTransitSchedule().getTransitLines().values()) {
			for (TransitRoute transitRoute : transitLine.getRoutes().values()) {
				// FIXME: I have to normalize the facilities here - there are referenced facility objects here which are not in the scenario.
				// FIXME: This fact will VERY likely also lead to problems elsewhere.
				ListIterator<TransitRouteStop> i = transitRoute.getStops().listIterator();
				while (i.hasNext()) {
					TransitRouteStop transitRouteStop = i.next();
					TransitStopFacility stopFacility1 = transitRouteStop.getStopFacility();
					TransitStopFacility stopFacility = sourceScenario.getTransitSchedule().getFacilities().get(stopFacility1.getId());
					if (stopFacility != null) {
						transitRouteStop.setStopFacility(stopFacility);
					} else {
						i.remove();
					}
				}
			}
		}
	}

	private static void splitTransitStopFacilities(EditableScenario targetScenario) {
		final Map<TransitStopFacility, List<TransitStopFacility>> facilityCopies = new HashMap<>();
		for (EditableTransitStopFacility transitStopFacility : targetScenario.getTransitSchedule().getEditableFacilities().values()) {
			facilityCopies.put(transitStopFacility, new ArrayList<>());
		}
		targetScenario.getTransitSchedule().getTransitLines().values().stream()
				.flatMap(transitLine -> transitLine.getRoutes().values().stream())
				.forEach(transitRoute -> transitRoute.getStops().stream()
						.filter(transitRouteStop -> transitRouteStop.getStopFacility().getLinkId() != null)
						.forEach(transitRouteStop -> {
							TransitStopFacility facility = transitRouteStop.getStopFacility();
							List<TransitStopFacility> copies = facilityCopies.get(facility);
							Link stopFacilityLink = targetScenario.getNetwork().getLinks().get(facility.getLinkId());
							List<Link> links = getLinks(transitRoute.getRoute(), targetScenario.getNetwork());
							// check if stop.link is on route. if not, but a link with the same toNode as stop.link is,
							// check if there is already a copy_link of stop. if it is, replace stop with copy. else create copy and replace stop with copy.
							if (!links.contains(stopFacilityLink)) {
								links.stream()
										.filter(link -> link.getToNode().equals(stopFacilityLink.getToNode()))
										.findFirst() // The first link on the route which has the same toNode of stop.link
										.ifPresent(link -> {
											// Create a copy of the stop which uses that link
											TransitStopFacility facilityCopy = copies.stream()
													.filter(copy -> copy.getLinkId().equals(stopFacilityLink.getId()))
													.findAny() // except if there already is one
													.orElseGet(() -> {
														Id<TransitStopFacility> newId = Id.create(facility.getId().toString() + "." + Integer.toString(copies.size() + 1), TransitStopFacility.class);
														TransitStopFacility newFacilityCopy = targetScenario.getTransitSchedule().getFactory().createTransitStopFacility(newId, facility.getCoord(), facility.getIsBlockingLane());
														newFacilityCopy.setStopPostAreaId(facility.getId().toString());
														newFacilityCopy.setLinkId(link.getId());
														newFacilityCopy.setName(facility.getName());
														copies.add(newFacilityCopy);
														targetScenario.getTransitSchedule().addStopFacility(newFacilityCopy);
														return newFacilityCopy;
													});
											transitRouteStop.setStopFacility(facilityCopy); // ... and set it.
										});
							}

						})
				);
	}

	private static List<Link> getLinks(NetworkRoute route, Network network) {
		List<Link> links = new ArrayList<>();
		links.add(network.getLinks().get(route.getStartLinkId()));
		links.addAll(route.getLinkIds().stream().map(linkId -> network.getLinks().get(linkId)).collect(Collectors.toList()));
		links.add(network.getLinks().get(route.getEndLinkId()));
		return links;
	}

}