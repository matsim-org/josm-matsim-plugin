package org.matsim.contrib.josm.model;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.emissions.utils.EmissionUtils;
import org.matsim.contrib.josm.gui.Preferences;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Export {

	public static Scenario toScenario(NetworkModel networkModel) {
		Config config = ConfigUtils.createConfig();
		if (Preferences.isSupportTransit()) {
			config.transit().setUseTransit(true);
		}

		Scenario scenario = ScenarioUtils.createScenario(config);
		for (MNode node : networkModel.nodes().values()) {
			Node newNode = scenario.getNetwork().getFactory().createNode(Id.createNodeId(node.getOrigId()), node.getCoord());
			scenario.getNetwork().addNode(newNode);
		}

		for (List<MLink> links : networkModel.getWay2Links().values()) {
			for (MLink link : links) {
				Link newLink = scenario
						.getNetwork()
						.getFactory()
						.createLink(Id.create(link.getOrigId(), Link.class),
								scenario.getNetwork().getNodes().get(Id.create(link.getFromNode().getOrigId(), Node.class)),
								scenario.getNetwork().getNodes().get(Id.create(link.getToNode().getOrigId(), Node.class)));
				newLink.setFreespeed(link.getFreespeed());
				newLink.setCapacity(link.getCapacity());
				newLink.setLength(link.getLength());
				newLink.setNumberOfLanes(link.getNumberOfLanes());
				newLink.setAllowedModes(link.getAllowedModes());
				if (Preferences.includeRoadType()) {
					//NetworkUtils.setType(newLink, link.getType());
					newLink.getAttributes().putAttribute(EmissionUtils.HBEFA_ROAD_TYPE, link.getType()) ;
				}
				scenario.getNetwork().addLink(newLink);
			}
		}

		final Map<StopArea, List<TransitStopFacility>> facilityCopies = createFacilities(networkModel, scenario);
		assert (scenario.getTransitSchedule().getFacilities().isEmpty());

		for (Line line : networkModel.lines().values()) {
			TransitLine newTLine = scenario.getTransitSchedule().getFactory().createTransitLine(line.getMatsimId());
			for (Route route : line.getRoutes()) {
				if (!route.isDeleted()) {
					List<TransitRouteStop> newTRStops = new ArrayList<>();
					List<Link> allLinks = getLinks(route.getRoute(), scenario.getNetwork());

					IntStream.range(0, route.getStops().size()).forEach(i -> {
						RouteStop transitRouteStop = route.getStops().get(i);
						TransitRouteStop newTRStop = scenario.getTransitSchedule().getFactory().createTransitRouteStop(null, transitRouteStop.getArrivalOffset(), transitRouteStop.getDepartureOffset());
						newTRStop.setAwaitDepartureTime(transitRouteStop.getAwaitDepartureTime());

						// check if stop.link is on route. if not, but a link with the same toNode as stop.link is,
						// check if there is already a copy_link of stop. if it is, replace stop with copy. else create copy and replace stop with copy.
						for (Link link : allLinks) {
							if (link == null) {
								throw new RuntimeException();
							}

							Optional<TransitStopFacility> first = facilityCopies.get(transitRouteStop.getStopArea()).stream().filter(facility -> {
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
								Optional<TransitStopFacility> first = facilityCopies.get(transitRouteStop.getStopArea()).stream().findFirst();
								if (first.isPresent()) {
									// Pick any link attached to the stop area as dummy start link.
									newTRStop.setStopFacility(first.get());
									if (first.get().getLinkId() != null) {
										allLinks.add(0, scenario.getNetwork().getLinks().get(first.get().getLinkId()));
										// It *still* can be null, because stop areas *without any* link are also allowed.
									}
								} else {
									throw new RuntimeException();
								}
							} else {
								Optional<TransitStopFacility> first = facilityCopies.get(transitRouteStop.getStopArea()).stream().findFirst();
								if (first.isPresent()) {
									// Pick any link attached to the stop area as dummy start link.
									newTRStop.setStopFacility(first.get());
								} else {
									throw new RuntimeException();
								}
							}
						}
						newTRStops.add(newTRStop);
						if (!scenario.getTransitSchedule().getFacilities().containsKey(newTRStop.getStopFacility().getId())) {
							scenario.getTransitSchedule().addStopFacility(newTRStop.getStopFacility());
						}
					});

					TransitRoute newTRoute = scenario.getTransitSchedule().getFactory().createTransitRoute(Id.create(route.getId(), TransitRoute.class), allLinks.isEmpty() ? null : RouteUtils.createNetworkRoute(allLinks.stream().map(Identifiable::getId).collect(Collectors.toList()), scenario.getNetwork()), newTRStops,
							route.getTransportMode());
					for (Departure departure : route.getDepartures()) {
						newTRoute.addDeparture(departure);
					}
					newTLine.addRoute(newTRoute);
				}
			}
			if (!newTLine.getRoutes().isEmpty()) {
				scenario.getTransitSchedule().addTransitLine(newTLine);
			}
		}
		return scenario;
	}

	private static Map<StopArea, List<TransitStopFacility>> createFacilities(NetworkModel networkModel, Scenario scenario) {
		final Map<StopArea, List<TransitStopFacility>> facilityCopies = new HashMap<>(networkModel.stopAreas().values().stream().collect(Collectors.toMap(Function.identity(), sa -> new ArrayList<>())));
		networkModel.stopAreas().values().stream().forEach(stopArea -> {
			List<MNode> stopPositionModelNodes = stopArea.getStopPositionOsmNodes().stream().map(osmNode -> getmNode(networkModel, osmNode))
					.filter(modelNode -> modelNode != null) //FIXME: don't even return these
					.collect(Collectors.toList());

			if (Preferences.isTransitLite() || stopPositionModelNodes.isEmpty()) {
				Id<TransitStopFacility> id = stopArea.getMatsimId();
				TransitStopFacility transitStopFacility = scenario.getTransitSchedule().getFactory().createTransitStopFacility(id, stopArea.getCoord(), stopArea.isBlockingLane());
				MLink linkId = stopArea.getLink();
				if (linkId != null) {
					transitStopFacility.setLinkId(Id.createLinkId(linkId.getOrigId()));
				}
				transitStopFacility.setName(stopArea.getName());
				facilityCopies.get(stopArea).add(transitStopFacility);
			} else {
				for (MNode modelNode : stopPositionModelNodes) {
					Node node = scenario.getNetwork().getNodes().get(Id.createNodeId(modelNode.getOrigId()));
					if(node.getInLinks().isEmpty()) {
						Id<TransitStopFacility> id = Id.create(stopArea.getMatsimId().toString(), TransitStopFacility.class);
						TransitStopFacility transitStopFacility = scenario.getTransitSchedule().getFactory().createTransitStopFacility(id, stopArea.getCoord(), stopArea.isBlockingLane());
						transitStopFacility.setName(stopArea.getName());
						facilityCopies.get(stopArea).add(transitStopFacility);
					} else {
						for (Link link : node.getInLinks().values()) {
							Id<TransitStopFacility> id = Id.create(stopArea.getMatsimId().toString() + "." + Integer.toString(facilityCopies.get(stopArea).size() + 1), TransitStopFacility.class);
							TransitStopFacility transitStopFacility = scenario.getTransitSchedule().getFactory().createTransitStopFacility(id, stopArea.getCoord(), stopArea.isBlockingLane());
							transitStopFacility.setLinkId(link.getId());
							transitStopFacility.setName(stopArea.getName());
							facilityCopies.get(stopArea).add(transitStopFacility);
						}
					}
				}
			}
			if (facilityCopies.get(stopArea).isEmpty()) {
				throw new RuntimeException();
			}
		});
		return facilityCopies;
	}

	private static MNode getmNode(NetworkModel networkModel, org.openstreetmap.josm.data.osm.Node osmNode) {
		MNode mNode = networkModel.nodes().get(osmNode);
		return mNode;
	}

	private static List<Link> getLinks(List<MLink> route, Network network) {
		return route.stream().map(link -> network.getLinks().get(link.getId())).collect(Collectors.toList());
	}

}