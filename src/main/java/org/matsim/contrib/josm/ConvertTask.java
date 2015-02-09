package org.matsim.contrib.josm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.LinkImpl;
import org.matsim.core.network.NodeImpl;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.population.routes.LinkNetworkRouteImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.io.OsmTransferException;
import org.xml.sax.SAXException;

/**
 * The Task that handles the convert action. Creates new OSM primitives with
 * MATSim Tag scheme
 * 
 * @author Nico
 * 
 */

class ConvertTask extends PleaseWaitRunnable {

	private MATSimLayer newLayer;

	/**
	 * Creates a new Convert task
	 * 
	 * @see PleaseWaitRunnable
	 */
	public ConvertTask() {
		super("Converting to MATSim Network");
	}

	/**
	 * @see org.openstreetmap.josm.gui.PleaseWaitRunnable#cancel()
	 */
	@Override
	protected void cancel() {
		// TODO Auto-generated method stub
	}

	/**
	 * @see org.openstreetmap.josm.gui.PleaseWaitRunnable#realRun()
	 */
	@Override
	protected void realRun() throws SAXException, IOException,
			OsmTransferException {
		this.progressMonitor.setTicksCount(9);
		this.progressMonitor.setTicks(0);

		// get layer data
		Layer layer = Main.main.getActiveLayer();

		// scenario for converted data
		Scenario sourceScenario = ScenarioUtils.createScenario(ConfigUtils
				.createConfig());
		sourceScenario.getConfig().scenario().setUseTransit(true);
		sourceScenario.getConfig().scenario().setUseVehicles(true);

		// scenario for new MATSim layer
		Scenario targetScenario = ScenarioUtils.createScenario(ConfigUtils
				.createConfig());
		Network network = targetScenario.getNetwork();
		targetScenario.getConfig().scenario().setUseTransit(true);
		targetScenario.getConfig().scenario().setUseVehicles(true);

		this.progressMonitor.setTicks(1);
		this.progressMonitor.setCustomText("converting osm data..");

		// convert layer data
		NewConverter
				.convertOsmLayer(
                        ((OsmDataLayer) layer),
                        sourceScenario,
                        new HashMap<Way, List<Link>>(),
                        new HashMap<Link, List<WaySegment>>(),
                        new HashMap<Relation, TransitRoute>(),
                        new HashMap<Id<TransitStopFacility>, OsmConvertDefaults.Stop>());

		// check if network should be cleaned
		if (Main.pref.getBoolean("matsim_cleanNetwork")) {
			this.progressMonitor.setTicks(2);
			this.progressMonitor.setCustomText("cleaning network..");
			new NetworkCleaner().run(sourceScenario.getNetwork());
		}

		this.progressMonitor.setTicks(3);
		this.progressMonitor.setCustomText("preparing data set..");

		// data set of new MATSim layer
		DataSet dataSet = new DataSet();

		// data mappings
		HashMap<Way, List<Link>> way2Links = new HashMap<>();
		HashMap<Link, List<WaySegment>> link2Segment = new HashMap<>();
		HashMap<Relation, TransitRoute> relation2Route = new HashMap<>();
		HashMap<Node, org.openstreetmap.josm.data.osm.Node> node2OsmNode = new HashMap<>();
		HashMap<Id<Link>, Way> linkId2Way = new HashMap<>();
		HashMap<Id<TransitStopFacility>, OsmConvertDefaults.Stop> stops = new HashMap<>();

		this.progressMonitor.setTicks(4);
		this.progressMonitor.setCustomText("loading nodes..");

		// create new OSM and MATSim nodes out of converted network nodes
		for (Node node : sourceScenario.getNetwork().getNodes().values()) {
			EastNorth coor = new EastNorth(node.getCoord().getX(), node.getCoord().getY());
			org.openstreetmap.josm.data.osm.Node nodeOsm = new org.openstreetmap.josm.data.osm.Node(coor);
			nodeOsm.put(ImportTask.NODE_TAG_ID, ((NodeImpl) node).getOrigId());
			node2OsmNode.put(node, nodeOsm);
			dataSet.addPrimitive(nodeOsm);
			Node newNode = network.getFactory()
					.createNode(
							Id.create(Long.toString(nodeOsm.getUniqueId()),
									Node.class), node.getCoord());
			((NodeImpl) newNode).setOrigId(((NodeImpl) node).getOrigId());
			network.addNode(newNode);
		}

		// create new ways and links out of converted network links
		this.progressMonitor.setTicks(5);
		this.progressMonitor.setCustomText("loading ways..");
		for (Link link : sourceScenario.getNetwork().getLinks().values()) {
			Way way = new Way();
			org.openstreetmap.josm.data.osm.Node fromNode = node2OsmNode
					.get(link.getFromNode());
			way.addNode(fromNode);
			org.openstreetmap.josm.data.osm.Node toNode = node2OsmNode.get(link
					.getToNode());
			way.addNode(toNode);
			way.put(ImportTask.WAY_TAG_ID, ((LinkImpl) link).getOrigId());
			way.put("freespeed", String.valueOf(link.getFreespeed()));
			way.put("capacity", String.valueOf(link.getCapacity()));
			way.put("length", String.valueOf(link.getLength()));
			way.put("permlanes", String.valueOf(link.getNumberOfLanes()));
			StringBuilder modes = new StringBuilder();

			for (String mode : link.getAllowedModes()) {
				modes.append(mode);
				if (link.getAllowedModes().size() > 1) {
					modes.append(";");
				}
			}
			way.put("modes", modes.toString());
			dataSet.addPrimitive(way);

			Link newLink = network.getFactory().createLink(
					Id.create(Long.toString(way.getUniqueId()), Link.class),
					network.getNodes().get(
							Id.create(Long.toString(fromNode.getUniqueId()),
									Node.class)),
					network.getNodes().get(
							Id.create(Long.toString(toNode.getUniqueId()),
									Node.class)));
			newLink.setFreespeed(link.getFreespeed());
			newLink.setCapacity(link.getCapacity());
			newLink.setLength(link.getLength());
			newLink.setNumberOfLanes(link.getNumberOfLanes());
			newLink.setAllowedModes(link.getAllowedModes());
			((LinkImpl) newLink).setOrigId(((LinkImpl) link).getOrigId());
			network.addLink(newLink);

			way2Links.put(way, Collections.singletonList(newLink));

			linkId2Way.put(link.getId(), way);
			link2Segment.put(newLink,
					Collections.singletonList(new WaySegment(way, 0)));
		}

		this.progressMonitor.setTicks(6);
		this.progressMonitor.setCustomText("loading stops..");
		for (TransitStopFacility stop : sourceScenario.getTransitSchedule()
				.getFacilities().values()) {
			TransitStopFacility newStop = targetScenario
					.getTransitSchedule()
					.getFactory()
					.createTransitStopFacility(stop.getId(), stop.getCoord(),
							stop.getIsBlockingLane());
			newStop.setName(stop.getName());

            EastNorth eastNorth = new EastNorth(stop.getCoord().getX(), stop.getCoord().getY());
			org.openstreetmap.josm.data.osm.Node platform = new org.openstreetmap.josm.data.osm.Node(eastNorth);
			platform.put("public_transport", "platform");
			platform.put("name", stop.getName());

			dataSet.addPrimitive(platform);

			org.openstreetmap.josm.data.osm.Node stopPosition = null;
			Way newWay = null;
			if (stop.getLinkId() != null) {

				newWay = linkId2Way.get(stop.getLinkId());
				List<Link> newWayLinks = way2Links.get(newWay);
				Link singleLink = newWayLinks.get(0);

				newStop.setLinkId(singleLink.getId());

				stopPosition = newWay.lastNode();
				stopPosition.put("public_transport", "stop_position");

				if (!stopPosition.hasKey("name")) {
					stopPosition.put("name", stop.getName());
				} else {
					stopPosition.put("name", stopPosition.get("name") + ";"
							+ stop.getName());
				}
			}

			targetScenario.getTransitSchedule().addStopFacility(newStop);

			Relation relation = new Relation();
			relation.put("matsim", "stop_relation");
			relation.put("id", stop.getId().toString());
			relation.put("name", stop.getName());
			if (newWay != null) {
				relation.addMember(new RelationMember("link", newWay));
			}
			if (stopPosition != null) {
				relation.addMember(new RelationMember("stop", stopPosition));
			}
			relation.addMember(new RelationMember("platform", platform));
			dataSet.addPrimitive(relation);

			stops.put(newStop.getId(), new OsmConvertDefaults.Stop(newStop,
					stopPosition, platform, newWay));

		}

		for (TransitLine line : sourceScenario.getTransitSchedule()
				.getTransitLines().values()) {
			TransitLine newLine = targetScenario.getTransitSchedule()
					.getFactory().createTransitLine(line.getId());
			for (TransitRoute route : line.getRoutes().values()) {

				Relation relation = new Relation();

				List<TransitRouteStop> newTransitStops = new ArrayList<>();

				for (TransitRouteStop tRStop : route.getStops()) {

					TransitStopFacility stop = targetScenario
							.getTransitSchedule().getFacilities()
							.get(tRStop.getStopFacility().getId());
					newTransitStops.add(targetScenario
							.getTransitSchedule()
							.getFactory()
							.createTransitRouteStop(stop,
									tRStop.getArrivalOffset(),
									tRStop.getDepartureOffset()));

					relation.addMember(new RelationMember("stop", stops
							.get(stop.getId()).position));
					relation.addMember(new RelationMember("platform", stops
							.get(stop.getId()).platform));
				}

				List<Id<Link>> links = new ArrayList<>();
				Id<Link> oldStartId = route.getRoute().getStartLinkId();
				Link oldStartLink = sourceScenario.getNetwork().getLinks()
						.get(oldStartId);
				Way newStartWay = linkId2Way.get(oldStartLink.getId());
				List<Link> newStartLinks = way2Links.get(newStartWay);
				Id<Link> startId = newStartLinks.get(0).getId();

				relation.addMember(new RelationMember("", linkId2Way.get(route
						.getRoute().getStartLinkId())));
				for (Id<Link> linkId : route.getRoute().getLinkIds()) {
					links.add(way2Links.get(linkId2Way.get(linkId)).get(0)
							.getId());
					relation.addMember(new RelationMember("", linkId2Way
							.get(linkId)));
				}
				Id<Link> oldEndId = route.getRoute().getEndLinkId();
				Link oldEndLink = sourceScenario.getNetwork().getLinks()
						.get(oldEndId);
				Way newEndWay = linkId2Way.get(oldEndLink.getId());
				List<Link> newEndLinks = way2Links.get(newEndWay);
				Id<Link> endId = newEndLinks.get(0).getId();
				relation.addMember(new RelationMember("", linkId2Way.get(route
						.getRoute().getEndLinkId())));

				NetworkRoute networkRoute = new LinkNetworkRouteImpl(startId,
						endId);
				networkRoute.setLinkIds(startId, links, endId);

				TransitRoute newRoute = targetScenario
						.getTransitSchedule()
						.getFactory()
						.createTransitRoute(route.getId(), networkRoute,
								newTransitStops, route.getTransportMode());
				newLine.addRoute(newRoute);
				relation.put("type", "route");
				relation.put("route", route.getTransportMode());
				relation.put("ref", line.getId().toString());

				dataSet.addPrimitive(relation);
				relation2Route.put(relation, newRoute);
			}
			targetScenario.getTransitSchedule().addTransitLine(newLine);
		}

		node2OsmNode.clear();
		linkId2Way.clear();

		this.progressMonitor.setTicks(8);
		this.progressMonitor.setCustomText("creating layer..");

		// create layer
		newLayer = new MATSimLayer(dataSet, MATSimLayer.createNewName(), null,
				targetScenario, way2Links, link2Segment, relation2Route, stops);
	}

	/**
	 * @see org.openstreetmap.josm.gui.PleaseWaitRunnable#finish()
	 */
	@Override
	protected void finish() {
		if (newLayer != null) {
			Main.main.addLayer(newLayer);
			Main.map.mapView.setActiveLayer(newLayer); // invoke layer change
														// event
		}
	}

}
