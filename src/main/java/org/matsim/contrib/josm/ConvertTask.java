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
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.pt.transitSchedule.TransitScheduleFactoryImpl;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.openstreetmap.josm.Main;
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
		this.progressMonitor.setTicksCount(8);
		this.progressMonitor.setTicks(0);

		// get layer data
		Layer layer = Main.main.getActiveLayer();

		// scenario for converted data
		Scenario tempScenario = ScenarioUtils.createScenario(ConfigUtils
				.createConfig());
		tempScenario.getConfig().scenario().setUseTransit(true);
		tempScenario.getConfig().scenario().setUseVehicles(true);

		// scenario for new MATSim layer
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils
				.createConfig());
		Network network = scenario.getNetwork();
		scenario.getConfig().scenario().setUseTransit(true);
		scenario.getConfig().scenario().setUseVehicles(true);

		this.progressMonitor.setTicks(1);
		this.progressMonitor.setCustomText("converting osm data..");

		// convert layer data
		NewConverter.convertOsmLayer(((OsmDataLayer) layer), tempScenario,
				new HashMap<Way, List<Link>>(),
				new HashMap<Link, List<WaySegment>>(),
				new HashMap<Relation, TransitRoute>(), new HashMap<Id<TransitStopFacility>, OsmConvertDefaults.Stop>());

		// check if network should be cleaned
		if (Main.pref.getBoolean("matsim_cleanNetwork")) {
			this.progressMonitor.setTicks(2);
			this.progressMonitor.setCustomText("cleaning network..");
			new NetworkCleaner().run(tempScenario.getNetwork());
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
		for (Node node : tempScenario.getNetwork().getNodes().values()) {

			Coord tmpCoor = node.getCoord();
			LatLon coor = new LatLon(tmpCoor.getY(), tmpCoor.getX());
			org.openstreetmap.josm.data.osm.Node nodeOsm = new org.openstreetmap.josm.data.osm.Node(
					coor);
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
		for (Link link : tempScenario.getNetwork().getLinks().values()) {
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
		this.progressMonitor
				.setCustomText("loading transit line stops and route relations..");

		// create new relations, transit routes and lines as well as stop
		// facilities out of converted transit schedule
		for (TransitLine line : tempScenario.getTransitSchedule()
				.getTransitLines().values()) {
			TransitLine newLine = scenario.getTransitSchedule().getFactory()
					.createTransitLine(line.getId());
			for (TransitRoute route : line.getRoutes().values()) {

				Relation relation = new Relation();

				List<TransitRouteStop> newTransitStops = new ArrayList<>();

				for (TransitRouteStop tRStop : route.getStops()) {

					Link oldStopLink = tempScenario.getNetwork().getLinks()
							.get(tRStop.getStopFacility().getLinkId());
					org.openstreetmap.josm.data.osm.Node osmNode = node2OsmNode
							.get(oldStopLink.getToNode());
					relation.addMember(new RelationMember("stop", osmNode));

					TransitStopFacility stop = NewConverter.createStopFacility(
							osmNode, relation, scenario.getTransitSchedule());
					stop.setName(tRStop.getStopFacility().getName());
					osmNode.put("name", tRStop.getStopFacility().getName());
					Way newWay = linkId2Way.get(oldStopLink.getId());
					List<Link> newWayLinks = way2Links.get(newWay);
					Link singleLink = newWayLinks.get(0);
					Id<Link> id = Id.createLinkId(singleLink.getId());
					stop.setLinkId(id);

					newTransitStops.add(scenario.getTransitSchedule()
							.getFactory().createTransitRouteStop(stop, 0, 0));
					scenario.getTransitSchedule().addStopFacility(stop);
				}

				List<Id<Link>> links = new ArrayList<>();
				Id<Link> oldStartId = route.getRoute().getStartLinkId();
				Link oldStartLink = tempScenario.getNetwork().getLinks().get(oldStartId);
				Way newStartWay = linkId2Way.get(oldStartLink.getId());
				List<Link> newStartLinks = way2Links.get(newStartWay);
				Id<Link> startId = newStartLinks.get(0).getId();
				
				relation.addMember(new RelationMember("", linkId2Way.get(route
						.getRoute().getStartLinkId())));
				for (Id<Link> linkId : route.getRoute().getLinkIds()) {
					links.add(way2Links.get(linkId2Way.get(linkId)).get(0).getId());
					relation.addMember(new RelationMember("", linkId2Way
							.get(linkId)));
				}
				Id<Link> oldEndId = route.getRoute().getEndLinkId();
				Link oldEndLink = tempScenario.getNetwork().getLinks().get(oldEndId);
				Way newEndWay = linkId2Way.get(oldEndLink.getId());
				List<Link> newEndLinks = way2Links.get(newEndWay);
				Id<Link> endId = newEndLinks.get(0).getId();
				relation.addMember(new RelationMember("", linkId2Way.get(route
						.getRoute().getEndLinkId())));

				NetworkRoute networkRoute = new LinkNetworkRouteImpl(startId,
						endId);
				networkRoute.setLinkIds(startId, links, endId);

				TransitRoute newRoute = scenario
						.getTransitSchedule()
						.getFactory()
						.createTransitRoute(route.getId(), networkRoute,
								newTransitStops, "pt");
				newLine.addRoute(newRoute);
				relation.put("type", "route");
				relation.put("route", route.getTransportMode());
				relation.put("ref", line.getId().toString());

				dataSet.addPrimitive(relation);
				relation2Route.put(relation, newRoute);
			}
			scenario.getTransitSchedule().addTransitLine(newLine);
		}

		node2OsmNode.clear();
		linkId2Way.clear();

		this.progressMonitor.setTicks(7);
		this.progressMonitor.setCustomText("creating layer..");

		// create layer
		newLayer = new MATSimLayer(dataSet, null, null, scenario, way2Links,
				link2Segment, relation2Route, stops);
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
