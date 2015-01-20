package org.matsim.contrib.josm;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.LinkImpl;
import org.matsim.core.network.MatsimNetworkReader;
import org.matsim.core.network.NodeImpl;
import org.matsim.core.population.routes.LinkNetworkRouteImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.io.UncheckedIOException;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.io.OsmTransferException;
import org.xml.sax.SAXException;

/**
 * The task which is executed after confirming the ImportDialog. Creates a new
 * layer showing the network data.
 * 
 * @author Nico
 */
class ImportTask extends PleaseWaitRunnable {

	/**
	 * The String representing the id tagging-key for nodes.
	 */
	public static final String NODE_TAG_ID = "id";
	/**
	 * The String representing the id tagging-key for ways.
	 */
	public static final String WAY_TAG_ID = "id";
	private final String networkPath;
	private final String schedulePath;
	private DataSet dataSet;
	private Scenario scenario;
	private HashMap<Way, List<Link>> way2Links;
	private HashMap<Link, List<WaySegment>> link2Segment;
	private HashMap<Relation, TransitRoute> relation2Route;
	private HashMap<Id<TransitStopFacility>, OsmConvertDefaults.Stop> stops;

	/**
	 * Creates a new Import task with the given <code>path</code>.
	 * 
	 * @param path
	 *            The path to be imported from
	 */
	public ImportTask(String networkPath, String schedulePath) {
		super("MATSim Import");
		this.networkPath = networkPath;
		this.schedulePath = schedulePath;
	}

	/**
	 * @see org.openstreetmap.josm.gui.PleaseWaitRunnable#cancel()
	 */
	@Override
	protected void cancel() {
		// TODO Auto-generated method stub
	}

	/**
	 * @see org.openstreetmap.josm.gui.PleaseWaitRunnable#finish()
	 */
	@Override
	protected void finish() {
		// layer = null happens if Exception happens during import,
		// as Exceptions are handled only after this method is called.
		MATSimLayer layer = new MATSimLayer(dataSet, networkPath, new File(
				networkPath), scenario, way2Links, link2Segment, relation2Route, stops);
		if (layer != null) {
			Main.main.addLayer(layer);
			Main.map.mapView.setActiveLayer(layer);
		}
	}

	/**
	 * @see org.openstreetmap.josm.gui.PleaseWaitRunnable#realRun()
	 */
	@Override
	protected void realRun() throws SAXException, IOException,
			OsmTransferException, UncheckedIOException {
		this.progressMonitor.setTicksCount(6);
		this.progressMonitor.setTicks(0);

		// prepare empty data set
		dataSet = new DataSet();

		String importSystem = (String) ImportDialog.importSystem
				.getSelectedItem();
		CoordinateTransformation ct = TransformationFactory
				.getCoordinateTransformation(importSystem,
						TransformationFactory.WGS84);

		this.progressMonitor.setTicks(1);
		this.progressMonitor.setCustomText("creating scenario..");

        Scenario tempScenario = readScenario();

		this.progressMonitor.setTicks(2);
		this.progressMonitor.setCustomText("reading network xml..");

		relation2Route = new HashMap<>();
		stops = new HashMap<>();
		way2Links = new HashMap<>();
		link2Segment = new HashMap<>();
		HashMap<Node, org.openstreetmap.josm.data.osm.Node> node2OsmNode = new HashMap<>();
		HashMap<Id<Link>, Way> linkId2Way = new HashMap<>();

		this.progressMonitor.setTicks(3);
		this.progressMonitor.setCustomText("creating nodes..");

        scenario = ScenarioUtils.createScenario(tempScenario.getConfig());
		for (Node node : tempScenario.getNetwork().getNodes().values()) {
			Coord tmpCoor = node.getCoord();
			LatLon coor;

			// convert coordinates into wgs84
			if (importSystem.equals("WGS84")) {
				coor = new LatLon(tmpCoor.getY(), tmpCoor.getX());
			} else {
				tmpCoor = ct.transform(new CoordImpl(tmpCoor.getX(), tmpCoor
						.getY()));
				coor = new LatLon(tmpCoor.getY(), tmpCoor.getX());
			}
			org.openstreetmap.josm.data.osm.Node nodeOsm = new org.openstreetmap.josm.data.osm.Node(
					coor);

			// set id of MATSim node as tag, as actual id of new MATSim node is
			// set as corresponding OSM node id
			nodeOsm.put(NODE_TAG_ID, node.getId().toString());
			node2OsmNode.put(node, nodeOsm);
			dataSet.addPrimitive(nodeOsm);
			Node newNode = scenario
					.getNetwork()
					.getFactory()
					.createNode(Id.create(nodeOsm.getUniqueId(), Node.class),
							node.getCoord());
			((NodeImpl) newNode).setOrigId(node.getId().toString());
			scenario.getNetwork().addNode(newNode);
		}

		this.progressMonitor.setTicks(4);
		this.progressMonitor.setCustomText("creating ways..");
		for (Link link : tempScenario.getNetwork().getLinks().values()) {
			Way way = new Way();
			org.openstreetmap.josm.data.osm.Node fromNode = node2OsmNode
					.get(link.getFromNode());
			way.addNode(fromNode);
			org.openstreetmap.josm.data.osm.Node toNode = node2OsmNode.get(link
					.getToNode());
			way.addNode(toNode);
			// set id of link as tag, as actual id of new link is set as
			// corresponding way id
			way.put(WAY_TAG_ID, link.getId().toString());
			way.put("freespeed", String.valueOf(link.getFreespeed()));
			way.put("capacity", String.valueOf(link.getCapacity()));
			way.put("length", String.valueOf(link.getLength()));
			way.put("permlanes", String.valueOf(link.getNumberOfLanes()));
			StringBuilder modes = new StringBuilder();

			// multiple values are separated by ";"
			for (String mode : link.getAllowedModes()) {
				modes.append(mode);
				if (link.getAllowedModes().size() > 1) {
					modes.append(";");
				}
			}
			way.put("modes", modes.toString());

			dataSet.addPrimitive(way);
			Link newLink = scenario
					.getNetwork()
					.getFactory()
					.createLink(
							Id.create(way.getUniqueId(), Link.class),
							scenario.getNetwork()
									.getNodes()
									.get(Id.create(fromNode.getUniqueId(),
											Node.class)),
							scenario.getNetwork()
									.getNodes()
									.get(Id.create(toNode.getUniqueId(),
											Node.class)));
			newLink.setFreespeed(link.getFreespeed());
			newLink.setCapacity(link.getCapacity());
			newLink.setLength(link.getLength());
			newLink.setNumberOfLanes(link.getNumberOfLanes());
			newLink.setAllowedModes(link.getAllowedModes());
			((LinkImpl) newLink).setOrigId(link.getId().toString());
			scenario.getNetwork().addLink(newLink);
			way2Links.put(way, Collections.singletonList(newLink));
			linkId2Way.put(link.getId(), way);
			link2Segment.put(newLink,
					Collections.singletonList(new WaySegment(way, 0)));
		}
		
		
		for (TransitStopFacility stop: tempScenario.getTransitSchedule().getFacilities().values()) {
			TransitStopFacility newStop = scenario.getTransitSchedule().getFactory().createTransitStopFacility(stop.getId(), stop.getCoord(), stop.getIsBlockingLane());
			newStop.setName(stop.getName());
			
			
			Coord tmpCoor = stop.getCoord();
			LatLon coor;
			// convert coordinates into wgs84
			if (importSystem.equals("WGS84")) {
				coor = new LatLon(tmpCoor.getY(), tmpCoor.getX());
			} else {
				tmpCoor = ct.transform(new CoordImpl(tmpCoor.getX(), tmpCoor
						.getY()));
				coor = new LatLon(tmpCoor.getY(), tmpCoor.getX());
			}
			org.openstreetmap.josm.data.osm.Node platform = new org.openstreetmap.josm.data.osm.Node(
					coor);
			platform.put("public_transport", "platform");
			platform.put("name", stop.getName());
		
			dataSet.addPrimitive(platform);
			
			
			org.openstreetmap.josm.data.osm.Node stopPosition = null;
			Way newWay = null;
			
			if(stop.getLinkId()!=null) {
				
				newWay = linkId2Way.get(stop.getLinkId());
				List<Link> newWayLinks = way2Links.get(newWay);
				Link singleLink = newWayLinks.get(0);
				Id<Link> linkId = Id.createLinkId(singleLink.getId());
				newStop.setLinkId(linkId);
				
				stopPosition = newWay.lastNode();
				stopPosition.put("public_transport", "stop_position");
				
				if(!stopPosition.hasKey("name")) {
					stopPosition.put("name", stop.getName());
				} else {
					stopPosition.put("name", stopPosition.get("name")+";"+stop.getName());
				}
			}
			
			scenario.getTransitSchedule().addStopFacility(newStop);
			
			
			Relation relation = new Relation();
			relation.put("matsim", "stop_relation");
			relation.put("id", stop.getId().toString());
			relation.put("name", stop.getName());
			relation.addMember(new RelationMember("link", newWay));
			relation.addMember(new RelationMember("stop", stopPosition));
			relation.addMember(new RelationMember("platform", platform));
			dataSet.addPrimitive(relation);
			
			stops.put(newStop.getId(), new OsmConvertDefaults.Stop(newStop, stopPosition, platform));
			
		}
		
		// create new relations, transit routes and lines as well as stop

		this.progressMonitor.setTicks(4);
		this.progressMonitor.setCustomText("creating relations and routes..");
		for (TransitLine line : tempScenario.getTransitSchedule()
				.getTransitLines().values()) {
			TransitLine newLine = scenario.getTransitSchedule().getFactory()
					.createTransitLine(line.getId());
			for (TransitRoute route : line.getRoutes().values()) {

				Relation relation = new Relation();

				List<TransitRouteStop> newTransitStops = new ArrayList<>();

				for (TransitRouteStop tRStop : route.getStops()) {

					TransitStopFacility stop = scenario.getTransitSchedule().getFacilities().get(tRStop.getStopFacility().getId());
					newTransitStops.add(scenario.getTransitSchedule()
							.getFactory().createTransitRouteStop(stop, tRStop.getArrivalOffset(), tRStop.getDepartureOffset()));
					
					relation.addMember(new RelationMember("stop", stops.get(stop.getId()).position));
					relation.addMember(new RelationMember("platform", stops.get(stop.getId()).platform));
				}

				List<Id<Link>> links = new ArrayList<>();
				Id<Link> oldStartId = route.getRoute().getStartLinkId();
				Link oldStartLink = tempScenario.getNetwork().getLinks()
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
				Link oldEndLink = tempScenario.getNetwork().getLinks()
						.get(oldEndId);
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
								newTransitStops, route.getTransportMode());
				newLine.addRoute(newRoute);
				relation.put("type", "route");
				relation.put("route", route.getTransportMode());
				relation.put("ref", line.getId().toString());

				dataSet.addPrimitive(relation);
				relation2Route.put(relation, newRoute);
			}
			scenario.getTransitSchedule().addTransitLine(newLine);
		}

		this.progressMonitor.setTicks(5);
		this.progressMonitor.setCustomText("creating layer..");
	}

    private Scenario readScenario() {
        Config config = ConfigUtils.createConfig();
        if (schedulePath != null) {
            config.scenario().setUseTransit(true);
            config.scenario().setUseVehicles(true);
        }
        Scenario tempScenario = ScenarioUtils.createScenario(config);
        MatsimNetworkReader reader = new MatsimNetworkReader(tempScenario);
        reader.readFile(networkPath);
        if (schedulePath != null) {
            new TransitScheduleReader(tempScenario).readFile(schedulePath);
        }
        return tempScenario;
    }
}
