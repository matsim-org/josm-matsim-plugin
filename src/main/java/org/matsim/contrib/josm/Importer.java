package org.matsim.contrib.josm;

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
import org.matsim.pt.transitSchedule.api.*;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.data.projection.Projection;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

class Importer {

    private final String networkPath;
    private final String schedulePath;
    private Projection projection;
    private MATSimLayer layer;

    HashMap<Relation, TransitRoute> relation2Route = new HashMap<>();
    HashMap<Id<TransitStopFacility>, TransitStopFacility> stops = new HashMap<>();
    HashMap<Way, List<Link>> way2Links = new HashMap<>();
    HashMap<Link, List<WaySegment>> link2Segment = new HashMap<>();
    HashMap<Node, org.openstreetmap.josm.data.osm.Node> node2OsmNode = new HashMap<>();
    HashMap<Id<Link>, Way> linkId2Way = new HashMap<>();
    private DataSet dataSet;
    private Scenario sourceScenario;
    private Scenario targetScenario;

    public Importer(String networkPath, String schedulePath, Projection projection) {
        this.networkPath = networkPath;
        this.schedulePath = schedulePath;
        this.projection = projection;
    }

    public Importer(Scenario scenario, Projection projection) {
        this.sourceScenario = scenario;
        this.projection = projection;
        this.networkPath = null;
        this.schedulePath = null;
    }

    void run() {
        dataSet = new DataSet();
        if (sourceScenario == null) {
            sourceScenario = readScenario();
            copyIdsToOrigIds(sourceScenario);
        }
        targetScenario = ScenarioUtils.createScenario(sourceScenario.getConfig());
        convertNetwork();
        if (sourceScenario.getConfig().scenario().isUseTransit()) {
            convertStops();
            convertLines();
        }
        layer = new MATSimLayer(
                dataSet,
                networkPath==null ? MATSimLayer.createNewName() : networkPath,
                networkPath==null ? null : new File(networkPath),
                targetScenario,
                way2Links,
                link2Segment,
                relation2Route
        );
    }

    private void copyIdsToOrigIds(Scenario sourceScenario) {
        for (Node node : sourceScenario.getNetwork().getNodes().values()) {
            ((NodeImpl) node).setOrigId(node.getId().toString());
        }
        for (Link link : sourceScenario.getNetwork().getLinks().values()) {
            ((LinkImpl) link).setOrigId(link.getId().toString());
        }
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

    private void convertNetwork() {
        for (Node node : sourceScenario.getNetwork().getNodes().values()) {
            EastNorth eastNorth = new EastNorth(node.getCoord().getX(), node.getCoord().getY());
            LatLon latLon = projection.eastNorth2latlon(eastNorth);
            org.openstreetmap.josm.data.osm.Node nodeOsm = new org.openstreetmap.josm.data.osm.Node(latLon);

            // set id of MATSim node as tag, as actual id of new MATSim node is
            // set as corresponding OSM node id
            nodeOsm.put(ImportTask.NODE_TAG_ID, ((NodeImpl) node).getOrigId());
            node2OsmNode.put(node, nodeOsm);
            dataSet.addPrimitive(nodeOsm);
            Node newNode = targetScenario
                    .getNetwork()
                    .getFactory()
                    .createNode(Id.create(nodeOsm.getUniqueId(), Node.class),
                            node.getCoord());
            ((NodeImpl) newNode).setOrigId(((NodeImpl) node).getOrigId());
            targetScenario.getNetwork().addNode(newNode);
        }


        for (Link link : sourceScenario.getNetwork().getLinks().values()) {
            Way way = new Way();
            org.openstreetmap.josm.data.osm.Node fromNode = node2OsmNode
                    .get(link.getFromNode());
            way.addNode(fromNode);
            org.openstreetmap.josm.data.osm.Node toNode = node2OsmNode.get(link
                    .getToNode());
            way.addNode(toNode);
            // set id of link as tag, as actual id of new link is set as
            // corresponding way id
            way.put(ImportTask.WAY_TAG_ID, ((LinkImpl) link).getOrigId());
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
            Link newLink = targetScenario
                    .getNetwork()
                    .getFactory()
                    .createLink(
                            Id.create(way.getUniqueId()+"_0", Link.class),
                            targetScenario.getNetwork()
                                    .getNodes()
                                    .get(Id.create(fromNode.getUniqueId(),
                                            Node.class)),
                            targetScenario.getNetwork()
                                    .getNodes()
                                    .get(Id.create(toNode.getUniqueId(),
                                            Node.class)));
            newLink.setFreespeed(link.getFreespeed());
            newLink.setCapacity(link.getCapacity());
            newLink.setLength(link.getLength());
            newLink.setNumberOfLanes(link.getNumberOfLanes());
            newLink.setAllowedModes(link.getAllowedModes());
            ((LinkImpl) newLink).setOrigId(((LinkImpl) link).getOrigId());
            targetScenario.getNetwork().addLink(newLink);
            way2Links.put(way, Collections.singletonList(newLink));
            linkId2Way.put(link.getId(), way);
            link2Segment.put(newLink, Collections.singletonList(new WaySegment(way, 0)));
        }
    }

    private void convertStops() {
        for (TransitStopFacility stop: sourceScenario.getTransitSchedule().getFacilities().values()) {
            EastNorth eastNorth = new EastNorth(stop.getCoord().getX(), stop.getCoord().getY());
            LatLon latLon = projection.eastNorth2latlon(eastNorth);
            org.openstreetmap.josm.data.osm.Node platform = new org.openstreetmap.josm.data.osm.Node(latLon);
            platform.put("public_transport", "platform");
            platform.put("name", stop.getName());
            dataSet.addPrimitive(platform);
            Way newWay;
            Id<Link> linkId = null;
            Relation relation = new Relation();
            relation.put("matsim", "stop_relation");
            relation.put("id", stop.getId().toString());
            relation.addMember(new RelationMember("platform", platform));
            dataSet.addPrimitive(relation);
            if(stop.getLinkId() != null) {
                newWay = linkId2Way.get(stop.getLinkId());
                List<Link> newWayLinks = way2Links.get(newWay);
                Link singleLink = newWayLinks.get(0);
                linkId = Id.createLinkId(singleLink.getId());
                relation.addMember(new RelationMember("link", newWay));
            }
            TransitStopFacility newStop = targetScenario.getTransitSchedule().getFactory().createTransitStopFacility(Id.create(relation.getUniqueId(), TransitStopFacility.class), stop.getCoord(), stop.getIsBlockingLane());
            newStop.setName(stop.getName());
            newStop.setLinkId(linkId);
            targetScenario.getTransitSchedule().addStopFacility(newStop);
            stops.put(stop.getId(), newStop);
        }
    }

    private void convertLines() {
        for (TransitLine line : sourceScenario.getTransitSchedule().getTransitLines().values()) {
            Relation lineRelation = new Relation();
            lineRelation.put("type", "route_master");
            lineRelation.put("ref", line.getId().toString());
            TransitLine newLine = targetScenario.getTransitSchedule().getFactory().createTransitLine(Id.create(lineRelation.getUniqueId(), TransitLine.class));
            for (TransitRoute route : line.getRoutes().values()) {
                Relation routeRelation = new Relation();
                List<TransitRouteStop> newTransitStops = new ArrayList<>();
                for (TransitRouteStop tRStop : route.getStops()) {
                    TransitStopFacility stop = stops.get(tRStop.getStopFacility().getId());
                    Relation stopRelation = (Relation) dataSet.getPrimitiveById(Long.parseLong(stop.getId().toString()), OsmPrimitiveType.RELATION);
                    newTransitStops.add(targetScenario.getTransitSchedule().getFactory().createTransitRouteStop(stop, tRStop.getArrivalOffset(), tRStop.getDepartureOffset()));
                    routeRelation.addMember(stopRelation.firstMember());
                }
                List<Id<Link>> links = new ArrayList<>();
                NetworkRoute networkRoute = route.getRoute();
                NetworkRoute newNetworkRoute;
                if (networkRoute != null) {
                    Id<Link> oldStartId = networkRoute.getStartLinkId();
                    Way newStartWay = linkId2Way.get(oldStartId);
                    List<Link> newStartLinks = way2Links.get(newStartWay);
                    Id<Link> startId = newStartLinks.get(0).getId();
                    routeRelation.addMember(new RelationMember("", linkId2Way.get(networkRoute.getStartLinkId())));
                    for (Id<Link> linkId : networkRoute.getLinkIds()) {
                        links.add(way2Links.get(linkId2Way.get(linkId)).get(0).getId());
                        routeRelation.addMember(new RelationMember("", linkId2Way.get(linkId)));
                    }
                    Id<Link> oldEndId = networkRoute.getEndLinkId();
                    Link oldEndLink = sourceScenario.getNetwork().getLinks().get(oldEndId);
                    Way newEndWay = linkId2Way.get(oldEndLink.getId());
                    List<Link> newEndLinks = way2Links.get(newEndWay);
                    Id<Link> endId = newEndLinks.get(0).getId();
                    routeRelation.addMember(new RelationMember("", linkId2Way.get(networkRoute.getEndLinkId())));
                    newNetworkRoute = new LinkNetworkRouteImpl(startId,endId);
                    newNetworkRoute.setLinkIds(startId, links, endId);
                } else {
                    newNetworkRoute = null;
                }
                TransitRoute newRoute = targetScenario
                        .getTransitSchedule()
                        .getFactory()
                        .createTransitRoute(Id.create(routeRelation.getUniqueId(), TransitRoute.class), newNetworkRoute,
                                newTransitStops, route.getTransportMode());
                newLine.addRoute(newRoute);
                routeRelation.put("type", "route");
                routeRelation.put("route", route.getTransportMode());
                routeRelation.put("ref", route.getId().toString());
                dataSet.addPrimitive(routeRelation);
                lineRelation.addMember(new RelationMember(null, routeRelation));
                relation2Route.put(routeRelation, newRoute);
            }
            dataSet.addPrimitive(lineRelation);
            targetScenario.getTransitSchedule().addTransitLine(newLine);
        }
    }

    public MATSimLayer getLayer() {
        return layer;
    }

}
