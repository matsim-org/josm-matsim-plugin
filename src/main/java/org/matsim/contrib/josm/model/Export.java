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
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.facilities.Facility;
import org.matsim.pt.transitSchedule.api.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Export {

	public static EditableScenario toScenario(NetworkModel networkModel) {
		fixTransitSchedule(networkModel.getScenario());
		Config config = networkModel.getScenario().getConfig();
		config.transit().setUseTransit(true);
		EditableScenario scenario = EditableScenarioUtils.createScenario(config);

		// copy nodes with switched id fields
		for (Node node : networkModel.getScenario().getNetwork().getNodes().values()) {
			Node newNode = scenario.getNetwork().getFactory().createNode(Id.create(((NodeImpl) node).getOrigId(), Node.class), node.getCoord());
			scenario.getNetwork().addNode(newNode);
		}
		// copy links with switched id fields
		for (Link link : networkModel.getScenario().getNetwork().getLinks().values()) {
			Link newLink = scenario
					.getNetwork()
					.getFactory()
					.createLink(Id.create(((LinkImpl) link).getOrigId(), Link.class),
							scenario.getNetwork().getNodes().get(Id.create(((NodeImpl) link.getFromNode()).getOrigId(), Node.class)),
							scenario.getNetwork().getNodes().get(Id.create(((NodeImpl) link.getToNode()).getOrigId(), Node.class)));
			newLink.setFreespeed(link.getFreespeed());
			newLink.setCapacity(link.getCapacity());
			newLink.setLength(link.getLength());
			newLink.setNumberOfLanes(link.getNumberOfLanes());
			newLink.setAllowedModes(link.getAllowedModes());
			scenario.getNetwork().addLink(newLink);
		}
		TransitSchedule transitSchedule = scenario.getTransitSchedule();
		final Map<StopArea, List<TransitStopFacility>> facilityCopies = new HashMap<>(networkModel.getScenario().getTransitSchedule().getEditableFacilities().values().stream().collect(Collectors.toMap(sa -> (StopArea) sa, sa -> new ArrayList<>())));
		networkModel.getScenario().getTransitSchedule().getEditableFacilities().values().stream().map(facility -> (StopArea) facility).forEach(stopArea -> {
			List<NodeImpl> stopPositionModelNodes = stopArea.getStopPositionOsmNodes().stream().map(osmNode -> (NodeImpl) networkModel.getScenario().getNetwork().getNodes().get(Id.createNodeId(osmNode.getUniqueId())))
					.filter(modelNode -> modelNode != null) //FIXME: don't even return these
					.collect(Collectors.toList());

			if (stopPositionModelNodes.isEmpty()) {
				Id<TransitStopFacility> id = stopArea.getOrigId();
				TransitStopFacility transitStopFacility = transitSchedule.getFactory().createTransitStopFacility(id, stopArea.getCoord(), stopArea.getIsBlockingLane());
				Id<Link> linkId = stopArea.getLinkId();
				if (linkId != null) {
					Link oldLink = networkModel.getScenario().getNetwork().getLinks().get(linkId);
					Id<Link> newLinkId = Id.createLinkId(((LinkImpl) oldLink).getOrigId());
					transitStopFacility.setLinkId(newLinkId);
				}
				transitStopFacility.setName(stopArea.getName());
				transitSchedule.addStopFacility(transitStopFacility);
				facilityCopies.get(stopArea).add(transitStopFacility);
			} else {
				for (NodeImpl modelNode : stopPositionModelNodes) {
					Node node = scenario.getNetwork().getNodes().get(Id.createNodeId(modelNode.getOrigId()));
					for (Link link : node.getInLinks().values()) {
						Id<TransitStopFacility> id = Id.create(stopArea.getId().toString() + "." + Integer.toString(facilityCopies.get(stopArea).size() + 1), TransitStopFacility.class);
						TransitStopFacility transitStopFacility = transitSchedule.getFactory().createTransitStopFacility(id, stopArea.getCoord(), stopArea.getIsBlockingLane());
						transitStopFacility.setLinkId(link.getId());
						transitStopFacility.setName(stopArea.getName());
						transitSchedule.addStopFacility(transitStopFacility);
						facilityCopies.get(stopArea).add(transitStopFacility);
					}
				}
				if (facilityCopies.get(stopArea).isEmpty()) {
					throw new RuntimeException();
				}
			}
			if (facilityCopies.get(stopArea).isEmpty()) {
				throw new RuntimeException();
			}
		});

		for (EditableTransitLine line : networkModel.getScenario().getTransitSchedule().getEditableTransitLines().values()) {
			Id<TransitLine> lineId = Id.create(line.getRealId(), TransitLine.class);
			TransitLine newTLine = transitSchedule.getFactory().createTransitLine(lineId);
			transitSchedule.addTransitLine(newTLine);

			for (EditableTransitRoute route : line.getEditableRoutes().values()) {
				if (!route.isDeleted()) {
					List<Id<Link>> links = new ArrayList<>();
					NetworkRoute networkRoute = route.getRoute();
					if (networkRoute != null) {
						Id<Link> startLinkId = Id.createLinkId(((LinkImpl) networkModel.getScenario().getNetwork().getLinks()
								.get(networkRoute.getStartLinkId())).getOrigId());
						links.add(startLinkId);
						for (Id<Link> id : networkRoute.getLinkIds()) {
							links.add(Id.createLinkId(((LinkImpl) networkModel.getScenario().getNetwork().getLinks().get(id)).getOrigId()));
						}
						Id<Link> endLinkId = Id.createLinkId(((LinkImpl) networkModel.getScenario().getNetwork().getLinks().get(networkRoute.getEndLinkId()))
								.getOrigId());
						links.add(endLinkId);
					}

					List<TransitRouteStop> newTRStops = new ArrayList<>();
					IntStream.range(0, route.getStops().size()).forEach(i -> {
						TransitRouteStop transitRouteStop = route.getStops().get(i);
						Id<TransitStopFacility> stopId = ((EditableTransitStopFacility) transitRouteStop.getStopFacility()).getOrigId();
						TransitRouteStop newTRStop = transitSchedule.getFactory().createTransitRouteStop(null, transitRouteStop.getArrivalOffset(), transitRouteStop.getDepartureOffset());
						String awaitDepartureTime = String.valueOf(networkModel.getScenario().getTransitSchedule().getTransitStopsAttributes()
								.getAttribute(transitRouteStop.getStopFacility().getName() + "_" + route.getRealId(), "awaitDepartureTime"));
						if (awaitDepartureTime != null) {
							newTRStop.setAwaitDepartureTime(Boolean.parseBoolean(awaitDepartureTime));
						}

						StopArea stopArea = (StopArea) transitRouteStop.getStopFacility();
						List<Link> allLinks = getLinks(links, scenario.getNetwork());
						// check if stop.link is on route. if not, but a link with the same toNode as stop.link is,
						// check if there is already a copy_link of stop. if it is, replace stop with copy. else create copy and replace stop with copy.
						for (Link link : allLinks) {
							if (link == null) {
								throw new RuntimeException();
							}

							Optional<TransitStopFacility> first = facilityCopies.get(stopArea).stream().filter(facility -> {
								return link.getId().equals(facility.getLinkId());
							}).findFirst();
							if (first.isPresent()) {
								newTRStop.setStopFacility(first.get());
								break;
							}
						}
						if (newTRStop.getStopFacility() == null) {
							if (i == 0) {
								// If the first stop doesn't have a facility now, its node
								// may be the very first node of the route.
								// So pick any facility.
								Optional<TransitStopFacility> first = facilityCopies.get(stopArea).stream().findFirst();
								if (first.isPresent()) {
									// Pick any link attached to the stop area as dummy start link.
									newTRStop.setStopFacility(first.get());
									if (first.get().getLinkId() != null) {
										links.add(0, first.get().getLinkId());
										// It *still* can be null, because stop areas *without any* link are also allowed.
									}
								} else {
									throw new RuntimeException();
								}
							} else {
								Optional<TransitStopFacility> first = facilityCopies.get(stopArea).stream().findFirst();
								if (first.isPresent()) {
									// Pick any link attached to the stop area as dummy start link.
									newTRStop.setStopFacility(first.get());
								} else {
									throw new RuntimeException();
								}
							}
						}
						newTRStops.add(newTRStop);
					});

					Id<TransitRoute> routeId = route.getRealId();
					NetworkRoute networkRoute1;
					if (!links.isEmpty()) {
						networkRoute1 = RouteUtils.createNetworkRoute(links, scenario.getNetwork());
					} else {
						networkRoute1 = null;
					}
					TransitRoute newTRoute = transitSchedule.getFactory().createTransitRoute(routeId, networkRoute1, newTRStops,
							route.getTransportMode());
					for (Departure departure : route.getDepartures().values()) {
						newTRoute.addDeparture(departure);
					}
					newTLine.addRoute(newTRoute);
				}
			}
			if (newTLine.getRoutes().isEmpty()) {
				transitSchedule.removeTransitLine(newTLine);
			}
		}
		return scenario;
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

	private static List<Link> getLinks(List<Id<Link>> route, Network network) {
		return route.stream().map(linkId -> network.getLinks().get(linkId)).collect(Collectors.toList());
	}

}