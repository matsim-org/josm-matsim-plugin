package org.matsim.contrib.josm;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.network.NodeImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.pt.transitSchedule.api.*;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.data.osm.event.*;
import org.openstreetmap.josm.data.osm.visitor.AbstractVisitor;
import org.openstreetmap.josm.gui.dialogs.relation.sort.RelationSorter;

import java.util.*;

/**
 * Listens to changes in the dataset and their effects on the Network
 * 
 * 
 */
class NetworkListener implements DataSetListener, org.openstreetmap.josm.data.Preferences.PreferenceChangedListener {

	private final Scenario scenario;

    private final Map<Way, List<Link>> way2Links;
	private final Map<Link, List<WaySegment>> link2Segments;
	private final Map<Relation, TransitRoute> relation2Route;
    private DataSet data;
    private Collection<ScenarioDataChangedListener> listeners = new ArrayList<>();


    public void removeListener(ScenarioDataChangedListener listener) {
        listeners.remove(listener);
    }

    interface ScenarioDataChangedListener {
        public void notifyDataChanged();
    }

    void fireNotifyDataChanged() {
        for (ScenarioDataChangedListener listener : listeners) {
            listener.notifyDataChanged();
        }
    }

    void addListener(ScenarioDataChangedListener listener) {
        listeners.add(listener);
    }

    public NetworkListener(DataSet data, Scenario scenario, Map<Way, List<Link>> way2Links,
                           Map<Link, List<WaySegment>> link2Segments,
                           Map<Relation, TransitRoute> relation2Route)
			throws IllegalArgumentException {
        this.data = data;
        MATSimPlugin.addPreferenceChangedListener(this);
		this.scenario = scenario;
		this.way2Links = way2Links;
		this.link2Segments = link2Segments;
		this.relation2Route = relation2Route;
	}

    void visitAll() {
        MyAggregatePrimitivesVisitor visitor = new MyAggregatePrimitivesVisitor();
        for (Node node : data.getNodes()) {
            visitor.visit(node);
        }
        for (Way way : data.getWays()) {
            visitor.visit(way);
        }
        for (Relation relation : data.getRelations()) {
            visitor.visit(relation);
        }
        fireNotifyDataChanged();
    }

    @Override
	public void dataChanged(DataChangedEvent dataChangedEvent) {
        visitAll();
        removeEmptyLines();
        fireNotifyDataChanged();
    }

	@Override
	// convert all referred elements of the moved node
	public void nodeMoved(NodeMovedEvent moved) {
        MyAggregatePrimitivesVisitor aggregatePrimitivesVisitor = new MyAggregatePrimitivesVisitor();
        aggregatePrimitivesVisitor.visit(moved.getNode());
        fireNotifyDataChanged();
    }

    @Override
	public void otherDatasetChange(AbstractDatasetChangedEvent arg0) {
	}

	@Override
	// convert added primitive as well as the ones connected to it
	public void primitivesAdded(PrimitivesAddedEvent added) {
        MyAggregatePrimitivesVisitor aggregatePrimitivesVisitor = new MyAggregatePrimitivesVisitor();
        for (OsmPrimitive primitive : added.getPrimitives()) {
			if (primitive instanceof Way) {
                aggregatePrimitivesVisitor.visit((Way) primitive);
			} else if (primitive instanceof Relation) {
                aggregatePrimitivesVisitor.visit((Relation) primitive);
			} else if (primitive instanceof org.openstreetmap.josm.data.osm.Node) {
                aggregatePrimitivesVisitor.visit((org.openstreetmap.josm.data.osm.Node) primitive);
			}
		}
        fireNotifyDataChanged();
	}

	@Override
	// delete any MATSim reference to the removed element and invoke new
	// conversion of referring elements
	public void primitivesRemoved(PrimitivesRemovedEvent primitivesRemoved) {
        MyAggregatePrimitivesVisitor aggregatePrimitivesVisitor = new MyAggregatePrimitivesVisitor();
        for (OsmPrimitive primitive : primitivesRemoved.getPrimitives()) {
			if (primitive instanceof org.openstreetmap.josm.data.osm.Node) {
                aggregatePrimitivesVisitor.visit(((org.openstreetmap.josm.data.osm.Node) primitive));
			} else if (primitive instanceof Way) {
                aggregatePrimitivesVisitor.visit((Way) primitive);
			} else if (primitive instanceof Relation) {
                aggregatePrimitivesVisitor.visit((Relation) primitive);
			}
		}
        removeEmptyLines();
        fireNotifyDataChanged();
    }

	@Override
	// convert affected relation
	public void relationMembersChanged(RelationMembersChangedEvent arg0) {
        MyAggregatePrimitivesVisitor aggregatePrimitivesVisitor = new MyAggregatePrimitivesVisitor();
        aggregatePrimitivesVisitor.visit(arg0.getRelation());
        removeEmptyLines();
        fireNotifyDataChanged();
    }

    private void removeEmptyLines() {
        if (scenario.getConfig().scenario().isUseTransit()) {
            // We do not create lines independently for now so we have to remove empty lines
            Collection<TransitLine> linesToRemove = new ArrayList<>();
            for (TransitLine line : scenario.getTransitSchedule().getTransitLines().values()) {
                if (line.getRoutes().isEmpty()) {
                    linesToRemove.add(line);
                }
            }
            for (TransitLine transitLine : linesToRemove) {
                scenario.getTransitSchedule().removeTransitLine(transitLine);
            }
        }
    }


    @Override
	// convert affected elements and other connected elements
	public void tagsChanged(TagsChangedEvent changed) {
        MyAggregatePrimitivesVisitor aggregatePrimitivesVisitor = new MyAggregatePrimitivesVisitor();
		for (OsmPrimitive primitive : changed.getPrimitives()) {
			if (primitive instanceof Way) {
                aggregatePrimitivesVisitor.visit((Way) primitive);
			} else if (primitive instanceof org.openstreetmap.josm.data.osm.Node) {
                aggregatePrimitivesVisitor.visit((org.openstreetmap.josm.data.osm.Node) primitive);
			} else if (primitive instanceof Relation) {
                aggregatePrimitivesVisitor.visit((Relation) primitive);
			}
		}
        fireNotifyDataChanged();
    }

	@Override
	// convert affected elements and other connected elements
	public void wayNodesChanged(WayNodesChangedEvent changed) {
        MyAggregatePrimitivesVisitor aggregatePrimitivesVisitor = new MyAggregatePrimitivesVisitor();
        aggregatePrimitivesVisitor.visit(changed.getChangedWay());
        fireNotifyDataChanged();
	}

    private void searchAndRemoveRoute(TransitRoute route) {
        // We do not know what line the route is in, so we have to search for it.
        for (TransitLine line : scenario.getTransitSchedule().getTransitLines().values()) {
            line.removeRoute(route);
        }
    }

    @Override
    public void preferenceChanged(org.openstreetmap.josm.data.Preferences.PreferenceChangeEvent e) {
        if (e.getKey().equalsIgnoreCase("matsim_keepPaths")
                || e.getKey().equalsIgnoreCase("matsim_filterActive")
                || e.getKey().equalsIgnoreCase("matsim_filter_hierarchy")) {
            visitAll();
            removeEmptyLines();
        }
        fireNotifyDataChanged();
    }

    public Scenario getScenario() {
        return scenario;
    }

    class MyAggregatePrimitivesVisitor extends AbstractVisitor {

        final Collection<OsmPrimitive> visited = new HashSet<>();

        void convertWay(Way way) {
            List<Link> links = new ArrayList<>();

            if (way.getNodesCount() > 1 && (way.hasTag(NewConverter.TAG_HIGHWAY, OsmConvertDefaults.getWayDefaults().keySet())
                    || NewConverter.meetsMatsimReq(way.getKeys())
                    || (way.hasTag(NewConverter.TAG_RAILWAY, OsmConvertDefaults.getWayDefaults().keySet())))) {
                List<Node> nodeOrder = new ArrayList<>();
                StringBuilder nodeOrderLog = new StringBuilder();
                for (int l = 0; l < way.getNodesCount(); l++) {
                    Node current = way.getNode(l);
                    if (l == 0 || l == way.getNodesCount() - 1 || Preferences.isKeepPaths()) {
                        nodeOrder.add(current);
                        nodeOrderLog.append("(").append(l).append(") ");
                    } else if (current.equals(way.getNode(way.getNodesCount() - 1))) {
                        nodeOrder.add(current); // add node twice if it occurs
                                                // twice in a loop so length
                                                // to this node is not
                                                // calculated wrong
                        nodeOrderLog.append("(").append(l).append(") ");
                    } else if (current.isConnectionNode()) {
                        for (OsmPrimitive prim : current.getReferrers()) {
                            if (prim instanceof Way && !prim.equals(way)) {
                                if (prim.hasKey(NewConverter.TAG_HIGHWAY)
                                        || NewConverter.meetsMatsimReq(prim.getKeys())) {
                                    nodeOrder.add(current);
                                    nodeOrderLog.append("(").append(l).append(") ");
                                    break;
                                }
                            }
                        }
                    } else {
                        for (OsmPrimitive prim : current.getReferrers()) {
                            if (prim instanceof Relation
                                    && prim.hasTag("route", "train", "track", "bus",
                                    "light_rail", "tram", "subway")
                                    && prim.hasTag("type", "route")
                                    && !nodeOrder.contains(current)) {
                                nodeOrder.add(current);
                            }
                        }
                    }
                }
                for (Node node : nodeOrder) {
                    NewConverter.checkNode(scenario.getNetwork(), node);
                }

                Double capacity = 0.;
                Double freespeed = 0.;
                Double nofLanes = 0.;
                boolean oneway = true;
                boolean onewayReverse = false;

                if (way.getKeys().containsKey(NewConverter.TAG_HIGHWAY)
                        || way.getKeys().containsKey(NewConverter.TAG_RAILWAY)) {

                    String wayType;
                    if (way.getKeys().containsKey(NewConverter.TAG_HIGHWAY)) {
                        wayType = way.getKeys().get(NewConverter.TAG_HIGHWAY);
                    } else if (way.getKeys().containsKey(NewConverter.TAG_RAILWAY)) {
                        wayType = way.getKeys().get(NewConverter.TAG_RAILWAY);
                    } else {
                        return;
                    }

                    // load defaults
                    OsmConvertDefaults.OsmWayDefaults defaults = OsmConvertDefaults.getWayDefaults().get(wayType);
                    if (defaults != null) {

                        if (defaults.hierarchy > Main.pref.getInteger(
                                "matsim_filter_hierarchy", 6)) {
                            return;
                        }
                        nofLanes = defaults.lanes;
                        double laneCapacity = defaults.laneCapacity;
                        freespeed = defaults.freespeed;
                        oneway = defaults.oneway;

                        // check if there are tags that overwrite defaults
                        // - check tag "junction"
                        if ("roundabout".equals(way.getKeys().get(NewConverter.TAG_JUNCTION))) {
                            // if "junction" is not set in tags, get()
                            // returns null and
                            // equals()
                            // evaluates to false
                            oneway = true;
                        }

                        // check tag "oneway"
                        String onewayTag = way.getKeys().get(NewConverter.TAG_ONEWAY);
                        if (onewayTag != null) {
                            if ("yes".equals(onewayTag)) {
                                oneway = true;
                            } else if ("true".equals(onewayTag)) {
                                oneway = true;
                            } else if ("1".equals(onewayTag)) {
                                oneway = true;
                            } else if ("-1".equals(onewayTag)) {
                                onewayReverse = true;
                                oneway = false;
                            } else if ("no".equals(onewayTag)) {
                                oneway = false; // may be used to overwrite
                                                // defaults
                            }
                        }

                        // In case trunks, primary and secondary roads are
                        // marked as
                        // oneway,
                        // the default number of lanes should be two instead
                        // of one.
                        if (wayType.equalsIgnoreCase("trunk")
                                || wayType.equalsIgnoreCase("primary")
                                || wayType.equalsIgnoreCase("secondary")) {
                            if (oneway && nofLanes == 1.0) {
                                nofLanes = 2.0;
                            }
                        }

                        String maxspeedTag = way.getKeys().get(NewConverter.TAG_MAXSPEED);
                        if (maxspeedTag != null) {
                            try {
                                freespeed = Double.parseDouble(maxspeedTag) / 3.6; // convert
                                // km/h to
                                // m/s
                            } catch (NumberFormatException e) {
                            }
                        }

                        // check tag "lanes"
                        String lanesTag = way.getKeys().get(NewConverter.TAG_LANES);
                        if (lanesTag != null) {
                            try {
                                double tmp = Double.parseDouble(lanesTag);
                                if (tmp > 0) {
                                    nofLanes = tmp;
                                }
                            } catch (Exception e) {
                            }
                        }
                        // create the link(s)
                        capacity = nofLanes * laneCapacity;
                    }
                }
                if (way.getKeys().containsKey("capacity")) {
                    Double capacityTag = NewConverter.parseDoubleIfPossible(way.getKeys()
                            .get("capacity"));
                    if (capacityTag != null) {
                        capacity = capacityTag;
                    } else {
                    }
                }
                if (way.getKeys().containsKey("freespeed")) {
                    Double freespeedTag = NewConverter.parseDoubleIfPossible(way.getKeys()
                            .get("freespeed"));
                    if (freespeedTag != null) {
                        freespeed = freespeedTag;
                    } else {
                    }
                }
                if (way.getKeys().containsKey("permlanes")) {
                    Double permlanesTag = NewConverter.parseDoubleIfPossible(way.getKeys()
                            .get("permlanes"));
                    if (permlanesTag != null) {
                        nofLanes = permlanesTag;
                    } else {
                    }
                }

                Double taggedLength = null;
                if (way.getKeys().containsKey("length")) {
                    Double temp = NewConverter.parseDoubleIfPossible(way.getKeys().get("length"));
                    if (temp != null) {
                        taggedLength = temp;

                    } else {
                    }
                }


                long increment = 0;
                for (int k = 1; k < nodeOrder.size(); k++) {
                    List<WaySegment> segs = new ArrayList<>();
                    Node nodeFrom = nodeOrder.get(k - 1);
                    Node nodeTo = nodeOrder.get(k);
                    int fromIdx = way.getNodes().indexOf(nodeFrom);
                    int toIdx = way.getNodes().indexOf(nodeTo);
                    if (fromIdx > toIdx) { // loop, take latter occurrence
                        toIdx = way.getNodes().lastIndexOf(nodeTo);
                    }
                    Double length = 0.;
                    for (int m = fromIdx; m < toIdx; m++) {
                        segs.add(new WaySegment(way, m));
                        length += way
                                .getNode(m)
                                .getCoor()
                                .greatCircleDistance(
                                        way.getNode(m + 1).getCoor());
                    }
                    if (taggedLength != null) {
                        if (length != 0.0) {
                            length = taggedLength * length / way.getLength();
                        } else {
                            length = taggedLength;
                        }
                    }
                    List<Link> tempLinks = NewConverter.createLink(scenario.getNetwork(), way, nodeFrom,
                            nodeTo, length, increment, oneway, onewayReverse,
                            freespeed, capacity, nofLanes, NewConverter.determineModes(way));
                    for (Link link : tempLinks) {
                        link2Segments.put(link, segs);
                    }
                    links.addAll(tempLinks);
                    increment++;
                }
            }
            way2Links.put(way, links);
        }

        @Override
        public void visit(Node node) {
            if (visited.add(node)) {
                if (node.isDeleted()) {
                    Id<org.matsim.api.core.v01.network.Node> id = Id.create(node.getUniqueId(), org.matsim.api.core.v01.network.Node.class);
                    if (scenario.getNetwork().getNodes().containsKey(id)) {
                        NodeImpl matsimNode = (NodeImpl) scenario.getNetwork().getNodes().get(id);
                        scenario.getNetwork().removeNode(matsimNode.getId());
                    }
                }
                // When a Node was touched, we need to look at ways (because their length may change)
                // and at relations (because it may be a transit stop)
                // which contain it.
                for (OsmPrimitive primitive : node.getReferrers()) {
                    primitive.accept(this);
                }
            }
        }

        @Override
        public void visit(Way way) {
            // When a Way is touched, we need to look at relations (because they may
            // be transit routes which have changed now).
            // I probably have to look at the nodes (because they may not be needed anymore),
            // but then I would probably traverse the entire net.
            if (visited.add(way)) {
                List<Link> oldLinks = way2Links.remove(way);
                if (oldLinks != null) {
                    for (Link link : oldLinks) {
                        Link removedLink = scenario.getNetwork().removeLink(link.getId());
                        link2Segments.remove(removedLink);
                    }
                }
                if (!way.isDeleted()) {
                    convertWay(way);
                }
                for (OsmPrimitive primitive : way.getReferrers()) {
                    primitive.accept(this);
                }
            }

        }

        @Override
        public void visit(Relation relation) {
            if (visited.add(relation)) {
                if (scenario.getConfig().scenario().isUseTransit()) {
                    // convert Relation, remove previous references in the MATSim data
                    TransitRoute route = relation2Route.remove(relation);
                    if (route != null) {
                        searchAndRemoveRoute(route);
                    }
                    Id<TransitStopFacility> transitStopFacilityId = Id.create(relation.getUniqueId(), TransitStopFacility.class);
                    if (scenario.getTransitSchedule().getFacilities().containsKey(transitStopFacilityId)) {
                        scenario.getTransitSchedule().removeStopFacility(scenario.getTransitSchedule().getFacilities().get(transitStopFacilityId));
                    }
                    if (!relation.isDeleted()) {
                        if (relation.hasTag("type", "route_master")) {
                            for (OsmPrimitive osmPrimitive : relation.getMemberPrimitives()) {
                                osmPrimitive.accept(this);
                            }
                        }
                        if (relation.hasTag("type", "route")) {
                            createTransitRoute(relation);
                        }
                        if (relation.hasTag("matsim", "stop_relation")) {
                            createStopFacility(relation);
                        }
                    }
                }
            }
        }

        private void createStopFacility(Relation relation) {
            Id<TransitStopFacility> transitStopFacilityId = Id.create(relation.getUniqueId(), TransitStopFacility.class);
            Link link = null;
            Node platform = null;
            for (RelationMember member : relation.getMembers()) {
                if (member.isWay() && member.hasRole("link")) {
                    Way way = member.getWay();
                    List<Link> links = way2Links.get(way);
                    if (links != null && !links.isEmpty()) {
                        link = links.get(links.size() - 1);
                    }
                } else if (member.isNode() && member.hasRole("platform")) {
                    platform = member.getNode();
                }
            }
            if (platform != null) {
                TransitStopFacility stop = scenario.getTransitSchedule().getFactory().createTransitStopFacility(
                        transitStopFacilityId,
                        new CoordImpl(platform.getEastNorth().getX(), platform.getEastNorth().getY()),
                        true);
                stop.setName(platform.getName());
                if (link != null) {
                    stop.setLinkId(link.getId());
                }
                scenario.getTransitSchedule().addStopFacility(stop);
            }
        }

        private void createTransitRoute(Relation relation) {
            RelationSorter sorter = new RelationSorter();
            sorter.sortMembers(relation.getMembers());
            ArrayList<TransitRouteStop> routeStops = new ArrayList<>();
            TransitSchedule schedule = scenario.getTransitSchedule();
            TransitScheduleFactory builder = schedule.getFactory();
            for (RelationMember member : relation.getMembers()) {
                if (member.isNode() && member.getMember().hasTag("public_transport", "platform")) {
                    TransitStopFacility facility = findFacilityForPlatform(member.getNode());
                    if (facility != null) {
                        routeStops.add(builder.createTransitRouteStop(facility, 0, 0));
                    }
                }
            }
            Id<TransitRoute> routeId = Id.create(relation.getUniqueId(), TransitRoute.class);
            Id<TransitLine> transitLineId = NewConverter.getTransitLineId(relation);
            TransitLine tLine;
            if (!scenario.getTransitSchedule().getTransitLines().containsKey(transitLineId)) {
                tLine = builder.createTransitLine(transitLineId);
                schedule.addTransitLine(tLine);
            } else {
                tLine = scenario.getTransitSchedule().getTransitLines().get(transitLineId);
            }
            NetworkRoute networkRoute = createNetworkRoute(relation);
            TransitRoute tRoute = builder.createTransitRoute(routeId, networkRoute, routeStops, relation.get("route"));
            tLine.addRoute(tRoute);
            relation2Route.put(relation, tRoute);
        }

        TransitStopFacility findFacilityForPlatform(Node node) {
            for (OsmPrimitive referrer : node.getReferrers()) {
                if (referrer instanceof Relation && referrer.hasTag("matsim", "stop_relation")) {
                    referrer.accept(this);
                    TransitStopFacility facility = scenario.getTransitSchedule().getFacilities()
                            .get(Id.create(referrer.getUniqueId(), TransitStopFacility.class));
                    if (facility != null) {
                        return facility;
                    }
                }
            }
            return null;
        }

        private NetworkRoute createNetworkRoute(Relation relation) {
            List<Id<Link>> links = new ArrayList<>();
            for (Way way : relation.getMemberPrimitives(Way.class)) {
                List<Link> wayLinks = way2Links.get(way);
                if (wayLinks != null) {
                    for (Link link : wayLinks) {
                        links.add(link.getId());
                    }
                }
            }
            if (links.isEmpty()) {
                return null;
            } else {
                return RouteUtils.createNetworkRoute(links, scenario.getNetwork());
            }
        }
    }


    public Map<Way, List<Link>> getWay2Links() {
        return way2Links;
    }

    public Map<Link, List<WaySegment>> getLink2Segments() {
        return link2Segments;
    }

    public Map<Relation, TransitRoute> getRelation2Route() {
        return relation2Route;
    }


}
