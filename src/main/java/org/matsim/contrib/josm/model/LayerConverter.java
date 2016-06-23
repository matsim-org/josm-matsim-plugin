package org.matsim.contrib.josm.model;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.josm.gui.Preferences;
import org.matsim.contrib.josm.scenario.EditableScenario;
import org.matsim.contrib.josm.scenario.EditableTransitStopFacility;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.pt.utils.CreatePseudoNetwork;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LayerConverter {

	public static MATSimLayer convertToPseudoNetwork(OsmDataLayer osmDataLayer) {

		NetworkModel networkModel = NetworkModel.createNetworkModel(osmDataLayer.data);
		networkModel.visitAll();
		EditableScenario sourceScenario = networkModel.getScenario();


		emptyNetwork(sourceScenario);
		fixTransitSchedule(sourceScenario);
		EditableScenario targetScenario = Export.convertIdsAndFilterDeleted(sourceScenario);

		new CreatePseudoNetwork(targetScenario.getTransitSchedule(), targetScenario.getNetwork(), "pt_")
				.createNetwork();

		Importer importer = new Importer(targetScenario);
		return importer.createMatsimLayer();
	}

	public static MATSimLayer convertWithFullTransit(OsmDataLayer osmLayer) {

		// convert layer data
		NetworkModel networkModel = NetworkModel.createNetworkModel((osmLayer).data);
		networkModel.visitAll();

		EditableScenario exportedScenario = Export.convertIdsAndFilterDeleted(networkModel.getScenario());
		splitTransitStopFacilities(exportedScenario);

		// check if network should be cleaned
		if ((!Preferences.isSupportTransit()) && Preferences.isCleanNetwork()) {
			new NetworkCleaner().run(exportedScenario.getNetwork());
		}
		Importer importer = new Importer(exportedScenario);
		return importer.createMatsimLayer();
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
							// check if stop.link is on route. if not, but a link with the same toNode as stop.link is,
							// check if there is already a copy_link of stop. if it is, replace stop with copy. else create copy and replace stop with copy.
							getLinks(transitRoute.getRoute(), targetScenario.getNetwork()).stream()
									.filter(link -> link.getToNode().equals(stopFacilityLink.getToNode()))
									.findFirst() // The first link on the route which has the same toNode of stop.link
									.filter(link -> ! link.getId().equals(facility.getLinkId())) // if it isn't actually stop.link (if it is, there is nothing to do)
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

	private static void emptyNetwork(EditableScenario sourceScenario) {
		for (Id<Link> linkId : new ArrayList<>(sourceScenario.getNetwork().getLinks().keySet())) {
			sourceScenario.getNetwork().removeLink(linkId);
		}
		for (Id<Node> nodeId : new ArrayList<>(sourceScenario.getNetwork().getNodes().keySet())) {
			sourceScenario.getNetwork().removeNode(nodeId);
		}
		for (EditableTransitStopFacility transitStopFacility : sourceScenario.getTransitSchedule().getEditableFacilities().values()) {
			transitStopFacility.setNodeId(null);
		}
		for (TransitLine transitLine : sourceScenario.getTransitSchedule().getTransitLines().values()) {
			for (TransitRoute transitRoute : transitLine.getRoutes().values()) {
				transitRoute.setRoute(null);
			}
		}
	}

	private static void fixTransitSchedule(EditableScenario sourceScenario) {
		for (TransitLine transitLine : sourceScenario.getTransitSchedule().getTransitLines().values()) {
			for (TransitRoute transitRoute : transitLine.getRoutes().values()) {
				transitRoute.setRoute(null);
				// FIXME: I have to normalize the facilities here - there are referenced facility objects here which are not in the scenario.
				// FIXME: This fact will VERY likely also lead to problems elsewhere.
				for (TransitRouteStop transitRouteStop : transitRoute.getStops()) {
					TransitStopFacility stopFacility1 = transitRouteStop.getStopFacility();
					TransitStopFacility stopFacility = sourceScenario.getTransitSchedule().getFacilities().get(stopFacility1.getId());
					transitRouteStop.setStopFacility(stopFacility);
				}
			}
		}
	}
}
