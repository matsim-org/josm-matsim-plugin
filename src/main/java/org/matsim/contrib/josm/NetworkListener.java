package org.matsim.contrib.josm;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.josm.scenario.EditableScenario;
import org.matsim.contrib.josm.scenario.EditableTransitLine;
import org.matsim.contrib.josm.scenario.EditableTransitRoute;
import org.matsim.core.network.NodeImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.search.SearchCompiler.Match;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.data.osm.event.*;
import org.openstreetmap.josm.data.osm.visitor.AbstractVisitor;
import org.openstreetmap.josm.data.osm.visitor.Visitor;
import org.openstreetmap.josm.gui.dialogs.relation.sort.RelationSorter;
import org.openstreetmap.josm.gui.dialogs.relation.sort.WayConnectionType;
import org.openstreetmap.josm.gui.dialogs.relation.sort.WayConnectionType.Direction;
import org.openstreetmap.josm.gui.dialogs.relation.sort.WayConnectionTypeCalculator;
import org.openstreetmap.josm.tools.Geometry;

import java.util.*;

/**
 * Listens to changes in the dataset and their effects on the Network
 * 
 * 
 */
class NetworkListener implements DataSetListener, org.openstreetmap.josm.data.Preferences.PreferenceChangedListener {

    private final EditableScenario scenario;

    private final Map<Way, List<Link>> way2Links;
    private final Map<Link, List<WaySegment>> link2Segments;
    private DataSet data;
    private Collection<ScenarioDataChangedListener> listeners = new ArrayList<>();

    public void removeListener(ScenarioDataChangedListener listener) {
	listeners.remove(listener);
    }

    interface ScenarioDataChangedListener {
	void notifyDataChanged();
    }

    void fireNotifyDataChanged() {
	for (ScenarioDataChangedListener listener : listeners) {
	    listener.notifyDataChanged();
	}
    }

    void addListener(ScenarioDataChangedListener listener) {
	listeners.add(listener);
    }

    public NetworkListener(DataSet data, EditableScenario scenario, Map<Way, List<Link>> way2Links,
	    Map<Link, List<WaySegment>> link2Segments) throws IllegalArgumentException {
	this.data = data;
	MATSimPlugin.addPreferenceChangedListener(this);
	this.scenario = scenario;
	this.way2Links = way2Links;
	this.link2Segments = link2Segments;
    }

    void visitAll() {
	ConvertVisitor visitor = new ConvertVisitor();
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
	fireNotifyDataChanged();
    }

    @Override
    // convert all referred elements of the moved node
    public void nodeMoved(NodeMovedEvent moved) {
	MyAggregatePrimitivesVisitor aggregatePrimitivesVisitor = new MyAggregatePrimitivesVisitor();
	aggregatePrimitivesVisitor.visit(moved.getNode());
	aggregatePrimitivesVisitor.finished();
	fireNotifyDataChanged();
    }

    class MyAggregatePrimitivesVisitor implements Visitor {

	Set<OsmPrimitive> primitives = new HashSet<>();

	@Override
	public void visit(Node node) {
	    primitives.add(node);
	    // When a Node was touched, we need to look at ways (because their
	    // length may change)
	    // and at relations (because it may be a transit stop)
	    // which contain it.
	    for (OsmPrimitive primitive : node.getReferrers()) {
		primitive.accept(this);
	    }
	}

	@Override
	public void visit(Way way) {
	    primitives.add(way);
	    // When a Way is touched, we need to look at relations (because they
	    // may
	    // be transit routes which have changed now).
	    // I probably have to look at the nodes (because they may not be
	    // needed anymore),
	    // but then I would probably traverse the entire net.
	    for (OsmPrimitive primitive : way.getReferrers()) {
		primitive.accept(this);
	    }
	}

	@Override
	public void visit(Relation relation) {
		if (relation.hasTag("type", "route_master")) {
			for (OsmPrimitive osmPrimitive : relation.getMemberPrimitives()) {
				osmPrimitive.accept(this);
			}
		}
		primitives.add(relation);
	}

	@Override
	public void visit(Changeset changeset) {

	}

	void finished() {
	    ConvertVisitor visitor = new ConvertVisitor();
	    for (Node node : OsmPrimitive.getFilteredList(primitives, Node.class)) {
		visitor.visit(node);
	    }
	    for (Way way : OsmPrimitive.getFilteredList(primitives, Way.class)) {
		visitor.visit(way);
	    }
	    for (Relation relation : OsmPrimitive.getFilteredList(primitives, Relation.class)) {
		visitor.visit(relation);
	    }
	}

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
			Way way = (Way) primitive;
			aggregatePrimitivesVisitor.visit(way);
			for (Node node : way.getNodes()) {
				aggregatePrimitivesVisitor.visit(node);
			}
		} else if (primitive instanceof Relation) {
		aggregatePrimitivesVisitor.visit((Relation) primitive);
	    } else if (primitive instanceof org.openstreetmap.josm.data.osm.Node) {
		aggregatePrimitivesVisitor.visit((org.openstreetmap.josm.data.osm.Node) primitive);
	    }
	}
	aggregatePrimitivesVisitor.finished();
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
	aggregatePrimitivesVisitor.finished();
	fireNotifyDataChanged();
    }

    @Override
    // convert affected relation
    public void relationMembersChanged(RelationMembersChangedEvent arg0) {
	MyAggregatePrimitivesVisitor aggregatePrimitivesVisitor = new MyAggregatePrimitivesVisitor();
	aggregatePrimitivesVisitor.visit(arg0.getRelation());
	for (OsmPrimitive primitive : arg0.getRelation().getMemberPrimitivesList()) {
	    if (primitive instanceof org.openstreetmap.josm.data.osm.Node) {
		aggregatePrimitivesVisitor.visit(((org.openstreetmap.josm.data.osm.Node) primitive));
	    } else if (primitive instanceof Way) {
		aggregatePrimitivesVisitor.visit((Way) primitive);
	    } else if (primitive instanceof Relation) {
		aggregatePrimitivesVisitor.visit((Relation) primitive);
	    }
	}
	aggregatePrimitivesVisitor.finished();
	fireNotifyDataChanged();
    }

    @Override
    // convert affected elements and other connected elements
    public void tagsChanged(TagsChangedEvent changed) {
	MyAggregatePrimitivesVisitor aggregatePrimitivesVisitor = new MyAggregatePrimitivesVisitor();
	for (OsmPrimitive primitive : changed.getPrimitives()) {
	    if (primitive instanceof Way) {
		aggregatePrimitivesVisitor.visit((Way) primitive);
		List<Link> links = way2Links.get(primitive);
		if (links != null) {
		    for (Link link : links) {
			aggregatePrimitivesVisitor.visit((Node) data.getPrimitiveById(
				Long.parseLong(link.getFromNode().getId().toString()), OsmPrimitiveType.NODE));
			aggregatePrimitivesVisitor.visit((Node) data.getPrimitiveById(
				Long.parseLong(link.getToNode().getId().toString()), OsmPrimitiveType.NODE));
		    }
		}
	    } else if (primitive instanceof org.openstreetmap.josm.data.osm.Node) {
		aggregatePrimitivesVisitor.visit((org.openstreetmap.josm.data.osm.Node) primitive);
	    } else if (primitive instanceof Relation) {
		aggregatePrimitivesVisitor.visit((Relation) primitive);
	    }
	}
	aggregatePrimitivesVisitor.finished();
	fireNotifyDataChanged();
    }

    @Override
    // convert affected elements and other connected elements
    public void wayNodesChanged(WayNodesChangedEvent changed) {
	MyAggregatePrimitivesVisitor aggregatePrimitivesVisitor = new MyAggregatePrimitivesVisitor();
	for (Node node : changed.getChangedWay().getNodes()) {
	    aggregatePrimitivesVisitor.visit(node);
	}
	List<Link> links = way2Links.get(changed.getChangedWay());
	if (links != null) {
	    for (Link link : links) {
		aggregatePrimitivesVisitor.visit((Node) data.getPrimitiveById(
			Long.parseLong(link.getFromNode().getId().toString()), OsmPrimitiveType.NODE));
		aggregatePrimitivesVisitor.visit((Node) data.getPrimitiveById(
			Long.parseLong(link.getToNode().getId().toString()), OsmPrimitiveType.NODE));
	    }
	}
	aggregatePrimitivesVisitor.visit((changed.getChangedWay()));
	aggregatePrimitivesVisitor.finished();
	fireNotifyDataChanged();
    }

    private void searchAndRemoveRoute(TransitRoute route) {
	// We do not know what line the route is in, so we have to search for
	// it.
	for (TransitLine line : scenario.getTransitSchedule().getTransitLines().values()) {
	    line.removeRoute(route);
	}
    }

    @Override
    public void preferenceChanged(org.openstreetmap.josm.data.Preferences.PreferenceChangeEvent e) {
	if (e.getKey().equalsIgnoreCase("matsim_keepPaths") || e.getKey().equalsIgnoreCase("matsim_filterActive")
		|| e.getKey().equalsIgnoreCase("matsim_filter_hierarchy")
		|| e.getKey().equalsIgnoreCase("matsim_transit_lite")) {
	    visitAll();
	}
	fireNotifyDataChanged();
    }

    public Scenario getScenario() {
	return scenario;
    }

    class ConvertVisitor extends AbstractVisitor {

	final Collection<OsmPrimitive> visited = new HashSet<>();

	void convertWay(Way way) {
	    List<Link> links = new ArrayList<>();

	    if (way.getNodesCount() > 1
		    && (way.hasTag(NewConverter.TAG_HIGHWAY, OsmConvertDefaults.getWayDefaults().keySet())
			    || NewConverter.meetsMatsimReq(way.getKeys()) || (way.hasTag(NewConverter.TAG_RAILWAY,
			    OsmConvertDefaults.getWayDefaults().keySet())))) {
		List<Node> nodeOrder = new ArrayList<>();

		for (Node current : way.getNodes()) {
		    if (scenario.getNetwork().getNodes()
			    .containsKey(Id.create(current.getUniqueId(), org.matsim.api.core.v01.network.Node.class))) {
			nodeOrder.add(current);
		    }
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

			if (defaults.hierarchy > Main.pref.getInteger("matsim_filter_hierarchy", 6)) {
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
			if (wayType.equalsIgnoreCase("trunk") || wayType.equalsIgnoreCase("primary")
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
		    Double capacityTag = NewConverter.parseDoubleIfPossible(way.getKeys().get("capacity"));
		    if (capacityTag != null) {
			capacity = capacityTag;
		    } else {
		    }
		}
		if (way.getKeys().containsKey("freespeed")) {
		    Double freespeedTag = NewConverter.parseDoubleIfPossible(way.getKeys().get("freespeed"));
		    if (freespeedTag != null) {
			freespeed = freespeedTag;
		    } else {
		    }
		}
		if (way.getKeys().containsKey("permlanes")) {
		    Double permlanesTag = NewConverter.parseDoubleIfPossible(way.getKeys().get("permlanes"));
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
			length += way.getNode(m).getCoor().greatCircleDistance(way.getNode(m + 1).getCoor());
		    }
		    if (taggedLength != null) {
			if (length != 0.0) {
			    length = taggedLength * length / way.getLength();
			} else {
			    length = taggedLength;
			}
		    }
		    List<Link> tempLinks = NewConverter.createLink(scenario.getNetwork(), way, nodeFrom, nodeTo,
			    length, increment, oneway, onewayReverse, freespeed, capacity, nofLanes,
			    NewConverter.determineModes(way));
		    for (Link link : tempLinks) {
			link2Segments.put(link, segs);
		    }
		    links.addAll(tempLinks);
		    increment++;
		}
	    }
	    way2Links.put(way, links);
	}

	void convertNode(Node node) {
	    if (node.evaluateCondition(new RelevantNodeMatch())) {
		NewConverter.createNode(scenario.getNetwork(), node);
	    }
	}

	@Override
	public void visit(Node node) {
	    if (visited.add(node)) {
		Id<org.matsim.api.core.v01.network.Node> id = Id.create(node.getUniqueId(),
				org.matsim.api.core.v01.network.Node.class);
		if (scenario.getNetwork().getNodes().containsKey(id)) {
		    NodeImpl matsimNode = (NodeImpl) scenario.getNetwork().getNodes().get(id);
		    scenario.getNetwork().removeNode(matsimNode.getId());
		}
		if (node.isDrawable()) {
		    convertNode(node);
		}
	    }
	}

	@Override
	public void visit(Way way) {
	    if (visited.add(way)) {
		List<Link> oldLinks = way2Links.remove(way);
		if (oldLinks != null) {
		    for (Link link : oldLinks) {
			Link removedLink = scenario.getNetwork().removeLink(link.getId());
			link2Segments.remove(removedLink);
		    }
		}
		if (way.isUsable()) {
		    convertWay(way);
		}
	    }
	}

		@Override
		public void visit(Relation relation) {
			if (visited.add(relation)) {
				if (scenario.getConfig().transit().isUseTransit()) {
					// convert Relation, remove previous references in the
					// MATSim data
					Id<TransitStopFacility> transitStopFacilityId = Id.create(relation.getUniqueId(),
							TransitStopFacility.class);
					if (scenario.getTransitSchedule().getFacilities().containsKey(transitStopFacilityId)) {
						scenario.getTransitSchedule().removeStopFacility(
								scenario.getTransitSchedule().getFacilities().get(transitStopFacilityId));
					}
					if (!relation.isDeleted()) {
						if (relation.hasTag("matsim", "stop_relation")) {
							createStopFacility(relation);
						}
					}
					EditableTransitRoute oldRoute = findRoute(relation);
					EditableTransitRoute newRoute = createTransitRoute(relation, oldRoute);
					if (oldRoute != null && newRoute == null) {
						oldRoute.setDeleted(true);
					} else if (oldRoute == null && newRoute != null) {
						TransitLine tLine = findOrCreateTransitLine(relation);
						tLine.addRoute(newRoute);
					} else if (oldRoute != null) {
						TransitLine tLine = findOrCreateTransitLine(relation);
						// The line the route is assigned to might have changed,
						// so remove it and add it again.
						searchAndRemoveRoute(oldRoute);
						tLine.addRoute(newRoute);
					}
					if (relation.isDeleted() && relation.hasTag("type", "route_master")) {
						EditableTransitLine transitLine = (EditableTransitLine) scenario.getTransitSchedule().getTransitLines()
								.get(Id.create(relation.getUniqueId(), TransitLine.class));
						if (transitLine != null) {
							for (EditableTransitRoute route : transitLine.getEditableRoutes().values()) {
								route.setDeleted(true);
							}
						}
					}
				}
			}
		}

	private void createStopFacility(Relation relation) {
	    Id<TransitStopFacility> transitStopFacilityId = Id
		    .create(relation.getUniqueId(), TransitStopFacility.class);
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
		TransitStopFacility stop = scenario
			.getTransitSchedule()
			.getFactory()
			.createTransitStopFacility(transitStopFacilityId,
				new CoordImpl(platform.getEastNorth().getX(), platform.getEastNorth().getY()), true);
		stop.setName(relation.get("id"));
		if (link != null) {
		    stop.setLinkId(link.getId());
		}
		scenario.getTransitSchedule().addStopFacility(stop);
	    }
	}

	private EditableTransitRoute createTransitRoute(Relation relation, EditableTransitRoute oldRoute) {
		if (!relation.isDeleted() && relation.hasTag("type", "route")) {
			TransitLine line = findOrCreateTransitLine(relation);
			if (line != null) {
				List<RelationMember> ways2Sort = new ArrayList<>();
				for (RelationMember member : relation.getMembers()) {
					if (member.isWay()) {
						ways2Sort.add(member);
					}
				}
				RelationSorter sorter = new RelationSorter();
				sorter.sortMembers(ways2Sort);
				List<TransitRouteStop> routeStops = new ArrayList<TransitRouteStop>();
				NetworkRoute networkRoute = null;
				if (Main.pref.getBoolean("matsim_transit_lite")) {
					for (RelationMember member : relation.getMembers()) {
						if (isStop(member)) {
							TransitStopFacility facility = findOrCreateFacilityLite(member.getMember());
							if (facility != null) {
								routeStops.add(scenario.getTransitSchedule().getFactory()
										.createTransitRouteStop(facility, 0, 0));
							}
						}
					}
				} else {
					for (RelationMember member : relation.getMembers()) {
						if (member.isNode() && member.getMember().hasTag("public_transport", "platform")) {
							TransitStopFacility facility = findFacilityForPlatform(member.getNode());
							if (facility != null) {
								routeStops.add(scenario.getTransitSchedule().getFactory()
										.createTransitRouteStop(facility, 0, 0));
							}
						}
					}
					networkRoute = createNetworkRoute(relation);
				}
				EditableTransitRoute newRoute;
				if (oldRoute == null) {
					Id<TransitRoute> routeId = Id.create(relation.getUniqueId(), TransitRoute.class);
					newRoute = new EditableTransitRoute(routeId);
				} else {
					// Edit the previous object in place.
					newRoute = oldRoute;
					newRoute.setDeleted(false);
				}

				newRoute.setRoute(networkRoute);
				newRoute.setTransportMode(relation.get("route"));
				newRoute.getStops().clear();
				newRoute.getStops().addAll(routeStops);
				newRoute.setDescription(relation.get("route"));
				String ref = relation.get("ref");
				newRoute.setRealId(ref != null ? Id.create(ref, TransitRoute.class) : newRoute.getId());
				return newRoute;
			}
		}
		return null; // not a route
	}

	private TransitStopFacility findOrCreateFacilityLite(OsmPrimitive primitive) {
	    String name = (primitive.getName() == null ? String.valueOf(primitive.getUniqueId()) : primitive.getName()
		    + "_" + +primitive.getUniqueId());
	    String id = String.valueOf(primitive.getUniqueId());
	    Id<TransitStopFacility> transitStopFacilityId = Id.create(id, TransitStopFacility.class);
	    if (scenario.getTransitSchedule().getFacilities().containsKey(transitStopFacilityId)) {
		return scenario.getTransitSchedule().getFacilities().get(transitStopFacilityId);
	    }
	    EastNorth eN = null;
	    if (primitive instanceof Way) {
		List<Node> nodes = ((Way) primitive).getNodes();
		if (nodes.size() > 2) {
		    // Apparently, only 2D-things have a centroid.
		    eN = Geometry.getCentroid(nodes);
		} else if (nodes.size() == 2) {
		    Node node0 = ((Way) primitive).getNodes().get(0);
		    Node node1 = ((Way) primitive).getNodes().get(1);
		    eN = node0.getEastNorth().getCenter(node1.getEastNorth());
		} else {
		    throw new RuntimeException();
		}
	    } else if (primitive instanceof Node) {
		eN = ((Node) primitive).getEastNorth();
	    }
	    if (eN == null) {
		return null;
	    }
	    TransitStopFacility stop = scenario.getTransitSchedule().getFactory()
		    .createTransitStopFacility(transitStopFacilityId, new CoordImpl(eN.getX(), eN.getY()), true);
	    stop.setName(name);

	    scenario.getTransitSchedule().addStopFacility(stop);
	    return stop;
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
	    if (!relation.getMembers().isEmpty()) { // WayConnectionTypeCalculator
		// will crash otherwise
		WayConnectionTypeCalculator calc = new WayConnectionTypeCalculator();
		List<WayConnectionType> connections = calc.updateLinks(relation.getMembers());

		for (Way way : relation.getMemberPrimitives(Way.class)) {
		    List<Link> wayLinks = way2Links.get(way);
		    if (wayLinks != null) {
			int i = relation.getMemberPrimitivesList().indexOf(way);
			if (connections.get(i).direction.equals(Direction.FORWARD)) {
			    for (Link link : wayLinks) {
				if (!link.getId().toString().endsWith("_r")) {
				    links.add(link.getId());
				}
			    }
			} else if (connections.get(i).direction.equals(Direction.BACKWARD)) {
			    // reverse order of links if backwards
			    Collections.reverse(wayLinks);
			    for (Link link : wayLinks) {
				if (link.getId().toString().endsWith("_r")) {
				    links.add(link.getId());
				}
			    }
			}
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

    private class RelevantNodeMatch extends Match {

	@Override
	public boolean match(OsmPrimitive osm) {
	    if (osm instanceof Node) {
		return match((Node) osm);
	    }
	    return false;
	}

	public boolean match(Node node) {
	    Way junctionWay = null;
	    for (Way way : OsmPrimitive.getFilteredList(node.getReferrers(), Way.class)) {
		if ((way.hasKey(NewConverter.TAG_HIGHWAY) || NewConverter.meetsMatsimReq(way.getKeys()))
			&& way.isUsable()) {
		    if (way.isFirstLastNode(node) || Preferences.isKeepPaths() || junctionWay != null) {
			return true;
		    } else {
			for (Relation relation : OsmPrimitive.getFilteredList(node.getReferrers(), Relation.class)) {
			    if (relation.hasTag("route", "train", "track", "bus", "light_rail", "tram", "subway")
				    && relation.hasTag("type", "route")) {
				return true;
			    }
			}
		    }
		    junctionWay = way;
		}
	    }
	    return false;
	}
    }

    EditableTransitRoute findRoute(OsmPrimitive maybeRelation) {
	if (maybeRelation instanceof Relation && scenario.getConfig().transit().isUseTransit()) {
	    for (EditableTransitLine editableTransitLine : scenario.getTransitSchedule().getEditableTransitLines()
		    .values()) {
		for (EditableTransitRoute transitRoute : editableTransitLine.getEditableRoutes().values()) {
		    if (transitRoute.getId().toString().equals(Long.toString(maybeRelation.getUniqueId()))) {
			return transitRoute;
		    }
		}
	    }
	}
	return null;
    }

	private TransitLine findOrCreateTransitLine(Relation route) {

		Id<TransitLine> transitLineId = null;
		for (OsmPrimitive primitive : route.getReferrers()) {
			if (primitive instanceof Relation
					&& primitive.hasTag("type", "route_master")) {
				transitLineId = Id.create(primitive.getUniqueId(), TransitLine.class);
			}
		}

		if (transitLineId == null) {
			return null;
		}
		EditableTransitLine tLine;

		if (!scenario.getTransitSchedule().getTransitLines().containsKey(transitLineId)) {
			tLine = new EditableTransitLine(transitLineId);
			scenario.getTransitSchedule().addTransitLine(tLine);
		} else {
			tLine = scenario.getTransitSchedule().getEditableTransitLines().get(transitLineId);
		}

		Relation maybeLineRelation = ((Relation) data.getPrimitiveById(Long.parseLong(transitLineId.toString()),
				OsmPrimitiveType.RELATION));

		fixTransitLineId(tLine, maybeLineRelation);
		return tLine;
	}

	private void fixTransitLineId(EditableTransitLine tLine, Relation maybeLineRelation) {
		if (maybeLineRelation != null) {
			String ref = maybeLineRelation.get("ref");
			if (ref != null) {
				tLine.setRealId(Id.create(ref, TransitLine.class));
			} else {
				tLine.setRealId(tLine.getId());
			}
		} else {
			tLine.setRealId(tLine.getId());
		}
	}

	public Map<Way, List<Link>> getWay2Links() {
	return way2Links;
    }

    public Map<Link, List<WaySegment>> getLink2Segments() {
	return link2Segments;
    }

    private boolean isStop(RelationMember member) {
	return (member.isNode() && member.getMember().hasTag("public_transport", "stop_position"))
		|| ((member.isNode() || member.isWay()) && member.getMember().hasTag("public_transport", "platform"));
    }

}
