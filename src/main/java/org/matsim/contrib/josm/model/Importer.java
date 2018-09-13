package org.matsim.contrib.josm.model;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.emissions.utils.EmissionUtils;
import org.matsim.contrib.josm.gui.Preferences;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;

public class Importer {

	private final File network;
	private final File schedule;

	HashMap<Id<TransitStopFacility>, Relation> stops = new HashMap<>();
	HashMap<Way, List<MLink>> way2Links = new HashMap<>();
	HashMap<Node, org.openstreetmap.josm.data.osm.Node> node2OsmNode = new HashMap<>();
	HashMap<Id<Link>, Way> linkId2Way = new HashMap<>();
	private DataSet dataSet;
	private Scenario sourceScenario;
	private HashMap<org.openstreetmap.josm.data.osm.Node, MNode> nodes = new HashMap<>();

	public Importer(File network, File schedule) {
		this.network = network;
		this.schedule = schedule;
	}

	public Importer(Scenario scenario) {
		this.sourceScenario = scenario;
		this.network = null;
		this.schedule = null;
	}

	public MATSimLayer createMatsimLayer() {
		dataSet = new DataSet();
		if (sourceScenario == null) {
			sourceScenario = readScenario();
		}
		convertNetwork();
		if (Preferences.isSupportTransit()) {
			convertStops();
			convertLines();
		}
		NetworkModel networkModel = NetworkModel.createNetworkModel(dataSet, way2Links);
		networkModel.visitAll();
		for (Line line : networkModel.lines().values()) {
			TransitLine matsimLine = sourceScenario.getTransitSchedule().getTransitLines().get(line.getMatsimId());
			for (Route route : line.getRoutes()) {
				TransitRoute matsimRoute = matsimLine.getRoutes().get(Id.create(route.getId(), TransitRoute.class));
				for (Departure departure : matsimRoute.getDepartures().values()) {
					route.addDeparture(departure);
				}
			}
		}
		return new MATSimLayer(dataSet, network == null ? MATSimLayer.createNewName() : network.getName(), network == null ? null : network, networkModel);
	}

	private Scenario readScenario() {
		Config config = ConfigUtils.createConfig();
		if (schedule != null) {
			config.transit().setUseTransit(true);
		}
		Scenario scenario = ScenarioUtils.createScenario(config);
		MatsimNetworkReader reader = new MatsimNetworkReader(scenario.getNetwork());
		reader.readFile(network.getAbsolutePath());
		if (schedule != null) {
			new TransitScheduleReader(scenario).readFile(schedule.getAbsolutePath());
		}
		return scenario;
	}

	private void convertNetwork() {
		for (Node node : sourceScenario.getNetwork().getNodes().values()) {
			EastNorth eastNorth = new EastNorth(node.getCoord().getX(), node.getCoord().getY());
			LatLon latLon = ProjectionRegistry.getProjection().eastNorth2latlon(eastNorth);
			org.openstreetmap.josm.data.osm.Node nodeOsm = new org.openstreetmap.josm.data.osm.Node(latLon);

			// set id of MATSim node as tag, as actual id of new MATSim node is
			// set as corresponding OSM node id
			nodeOsm.put(NodeConversionRules.ID, node.getId().toString());
			node2OsmNode.put(node, nodeOsm);
			dataSet.addPrimitive(nodeOsm);
			MNode newNode = new MNode(nodeOsm, node.getCoord());
			newNode.setOrigId(node.getId().toString());
			nodes.put(nodeOsm, newNode);
		}

		for (Link link : sourceScenario.getNetwork().getLinks().values()) {
			Way way = new Way();
			org.openstreetmap.josm.data.osm.Node fromNode = node2OsmNode.get(link.getFromNode());
			way.addNode(fromNode);
			org.openstreetmap.josm.data.osm.Node toNode = node2OsmNode.get(link.getToNode());
			way.addNode(toNode);
			// set id of link as tag, as actual id of new link is set as
			// corresponding way id
			way.put(LinkConversionRules.ID, link.getId().toString());
			way.put(LinkConversionRules.FREESPEED, String.valueOf(link.getFreespeed()));
			way.put(LinkConversionRules.CAPACITY, String.valueOf(link.getCapacity()));
			way.put(LinkConversionRules.LENGTH, String.valueOf(link.getLength()));
			way.put(LinkConversionRules.PERMLANES, String.valueOf(link.getNumberOfLanes()));

			if (EmissionUtils.getHbefaRoadType(link)!=null){
                way.put(LinkConversionRules.TYPE, String.valueOf(EmissionUtils.getHbefaRoadType(link)));
			}

			StringBuilder modes = new StringBuilder();
			for (String mode : link.getAllowedModes()) {
				modes.append(mode);
				if (link.getAllowedModes().size() > 1) {
					// multiple values are separated by ";"
					modes.append(";");
				}
			}
			way.put(LinkConversionRules.MODES, modes.toString());

			dataSet.addPrimitive(way);
			MLink newLink = new MLink(nodes.get(fromNode), nodes.get(toNode));
			newLink.setFreespeed(link.getFreespeed());
			newLink.setCapacity(link.getCapacity());
			newLink.setLength(link.getLength());
			newLink.setNumberOfLanes(link.getNumberOfLanes());
			newLink.setAllowedModes(link.getAllowedModes());
			newLink.setOrigId(link.getId().toString());
			newLink.setType(NetworkUtils.getType(link));
			newLink.setSegments(Collections.singletonList(new WaySegment(way, 0)));
			way2Links.put(way, Collections.singletonList(newLink));
			linkId2Way.put(link.getId(), way);
		}
	}

	private void convertStops() {
		for (TransitStopFacility stop : sourceScenario.getTransitSchedule().getFacilities().values()) {
			EastNorth eastNorth = new EastNorth(stop.getCoord().getX(), stop.getCoord().getY());
			LatLon latLon = ProjectionRegistry.getProjection().eastNorth2latlon(eastNorth);
			org.openstreetmap.josm.data.osm.Node platform = new org.openstreetmap.josm.data.osm.Node(latLon);
			platform.put("public_transport", "platform");
			if (stop.getName() != null) {
				platform.put("name", stop.getName());
			}
			dataSet.addPrimitive(platform);
			Relation relation = new Relation();
			relation.put("type", "public_transport");
			relation.put("public_transport", "stop_area");
			if (stop.getName() != null) {
				relation.put("name", stop.getName());
			}
			relation.put("ref", stop.getId().toString());
			relation.addMember(new RelationMember("platform", platform));
			if (stop.getLinkId() != null) {
				Way newWay = linkId2Way.get(stop.getLinkId());
				if (newWay == null) {
					throw new RuntimeException(stop.getLinkId().toString());
				}
				relation.addMember(new RelationMember("matsim:link", newWay));
			}
			dataSet.addPrimitive(relation);
			stops.put(stop.getId(), relation);
		}
	}

	private void convertLines() {
		for (TransitLine line : sourceScenario.getTransitSchedule().getTransitLines().values()) {
			Relation lineRelation = new Relation();
			lineRelation.put("type", "route_master");
			lineRelation.put("matsim:id", line.getId().toString());
			lineRelation.put("ref", line.getId().toString());
			for (TransitRoute route : line.getRoutes().values()) {
				Relation routeRelation = new Relation();
				for (TransitRouteStop tRStop : route.getStops()) {
					Relation stopArea = stops.get(tRStop.getStopFacility().getId());
					for (RelationMember member : stopArea.getMembers()) {
						if (member.hasRole("platform")) {
							OsmPrimitive platform = member.getMember();
							routeRelation.addMember(new RelationMember("platform", platform));
						}
					}
				}
				NetworkRoute networkRoute = route.getRoute();
				if (networkRoute != null) {
					routeRelation.addMember(new RelationMember("", linkId2Way.get(networkRoute.getStartLinkId())));
					for (Id<Link> linkId : networkRoute.getLinkIds()) {
						routeRelation.addMember(new RelationMember("", linkId2Way.get(linkId)));
					}
					routeRelation.addMember(new RelationMember("", linkId2Way.get(networkRoute.getEndLinkId())));
				}
				routeRelation.put("type", "route");
				routeRelation.put("route", route.getTransportMode());
				routeRelation.put("matsim:id", route.getId().toString());
				dataSet.addPrimitive(routeRelation);
				lineRelation.addMember(new RelationMember(null, routeRelation));
			}
			dataSet.addPrimitive(lineRelation);
		}
	}

}
