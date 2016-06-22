package org.matsim.contrib.josm;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.*;
import org.matsim.contrib.josm.scenario.EditableScenario;
import org.matsim.contrib.josm.scenario.EditableTransitLine;
import org.matsim.contrib.josm.scenario.EditableTransitRoute;
import org.matsim.contrib.josm.scenario.EditableTransitStopFacility;
import org.matsim.core.network.LinkImpl;
import org.matsim.core.network.NodeImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.search.SearchCompiler.Match;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.event.*;
import org.openstreetmap.josm.data.osm.visitor.AbstractVisitor;
import org.openstreetmap.josm.data.osm.visitor.Visitor;
import org.openstreetmap.josm.data.projection.Projection;
import org.openstreetmap.josm.data.projection.ProjectionChangeListener;
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
public class NetworkListener implements DataSetListener, org.openstreetmap.josm.data.Preferences.PreferenceChangedListener, ProjectionChangeListener {

	final static String TAG_HIGHWAY = "highway";
	final static String TAG_RAILWAY = "railway";
	private final EditableScenario scenario;

	private final Map<Way, List<Link>> way2Links;
	private final Map<Relation, TransitStopFacility> stopRelation2TransitStop;
	private final Map<Link, List<WaySegment>> link2Segments;
	private DataSet data;
	private Collection<ScenarioDataChangedListener> listeners = new ArrayList<>();

	interface ScenarioDataChangedListener {
		void notifyDataChanged();
	}

	private class RelevantNodeMatch extends Match {

		@Override
		public boolean match(OsmPrimitive osm) {
			return osm instanceof Node && match((Node) osm);
		}

		public boolean match(Node node) {
			if (isUsableAndNotRemoved(node)) {
				Way junctionWay = null;
				for (Way way : OsmPrimitive.getFilteredList(node.getReferrers(), Way.class)) {
					if (isUsableAndNotRemoved(way) && LinkConversionRules.isMatsimWay(way)) {
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
			}
			return false;
		}
	}

	public void removeListener(ScenarioDataChangedListener listener) {
		listeners.remove(listener);
	}

	void fireNotifyDataChanged() {
		for (ScenarioDataChangedListener listener : listeners) {
			listener.notifyDataChanged();
		}
	}

	void addListener(ScenarioDataChangedListener listener) {
		listeners.add(listener);
	}

	public NetworkListener(DataSet data, EditableScenario scenario, Map<Way, List<Link>> way2Links, Map<Link, List<WaySegment>> link2Segments,
						   Map<Relation, TransitStopFacility> stopRelation2TransitStop) throws IllegalArgumentException {
		this.data = data;
		MATSimPlugin.addPreferenceChangedListener(this);
		Main.addProjectionChangeListener(this);
		this.scenario = scenario;
		this.way2Links = way2Links;
		this.link2Segments = link2Segments;
		this.stopRelation2TransitStop = stopRelation2TransitStop;
	}

	public void visitAll() {
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

			// Annoyingly, JOSM removes the dataSet property from primitives
			// before calling this listener when
			// a primitive is "hard" deleted (not flagged as deleted).
			// So we have to check for this before asking for its referrers.
			if (node.getDataSet() != null) {
				for (OsmPrimitive primitive : node.getReferrers()) {
					primitive.accept(this);
				}
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

			// Annoyingly, JOSM removes the dataSet property from primitives
			// before calling this listener when
			// a primitive is "hard" deleted (not flagged as deleted).
			// So we have to check for this before asking for its referrers.
			if (way.getDataSet() != null) {
				for (OsmPrimitive primitive : way.getReferrers()) {
					primitive.accept(this);
				}
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
				Way way = (Way) primitive;
				aggregatePrimitivesVisitor.visit(way);
				for (Node node : way.getNodes()) {
					aggregatePrimitivesVisitor.visit(node);
				}
				aggregatePrimitivesVisitor.visit(way);
				List<Link> links = way2Links.get(way);
				if (links != null) {
					for (Link link : links) {
						aggregatePrimitivesVisitor.visit((Node) data.getPrimitiveById(Long.parseLong(link.getFromNode().getId().toString()),
								OsmPrimitiveType.NODE));
						aggregatePrimitivesVisitor.visit((Node) data.getPrimitiveById(Long.parseLong(link.getToNode().getId().toString()),
								OsmPrimitiveType.NODE));
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
				aggregatePrimitivesVisitor.visit((Node) data.getPrimitiveById(Long.parseLong(link.getFromNode().getId().toString()),
						OsmPrimitiveType.NODE));
				aggregatePrimitivesVisitor.visit((Node) data.getPrimitiveById(Long.parseLong(link.getToNode().getId().toString()),
						OsmPrimitiveType.NODE));
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
				|| e.getKey().equalsIgnoreCase("matsim_filter_hierarchy") || e.getKey().equalsIgnoreCase("matsim_transit_lite")) {
			visitAll();
		}
		fireNotifyDataChanged();
	}
	
	@Override
	public void projectionChanged(Projection oldValue, Projection newValue) {
		visitAll();
		fireNotifyDataChanged();
	}


	public Scenario getScenario() {
		return scenario;
	}

	class ConvertVisitor extends AbstractVisitor {

		final Collection<OsmPrimitive> visited = new HashSet<>();
		private final RelevantNodeMatch relevantNodeMatch = new RelevantNodeMatch();

		void convertWay(Way way) {
			final String wayType = LinkConversionRules.getWayType(way);
			final OsmConvertDefaults.OsmWayDefaults defaults = wayType != null ? OsmConvertDefaults.getWayDefaults().get(wayType) : null;

			final boolean forward = LinkConversionRules.isForward(way, defaults);
			final boolean backward = LinkConversionRules.isBackward(way, defaults);
			final Double freespeed = LinkConversionRules.getFreespeed(way, defaults);
			final Double nofLanesPerDirection = LinkConversionRules.getLanesPerDirection(way, defaults, forward, backward);
			final Double capacity = LinkConversionRules.getCapacity(way, defaults, nofLanesPerDirection);
			final Set<String> modes = LinkConversionRules.getModes(way, defaults);

			final Double taggedLength = LinkConversionRules.getTaggedLength(way);

			if (capacity != null && freespeed != null && nofLanesPerDirection != null && modes != null) {
				List<Node> nodeOrder = new ArrayList<>();
				for (Node current : way.getNodes()) {
					if (scenario.getNetwork().getNodes().containsKey(Id.create(NodeConversionRules.getId(current), org.matsim.api.core.v01.network.Node.class))) {
						nodeOrder.add(current);
					}
				}
				List<Link> links = new ArrayList<>();
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
					Double segmentLength = 0.;
					for (int m = fromIdx; m < toIdx; m++) {
						segs.add(new WaySegment(way, m));
						segmentLength += way.getNode(m).getCoor().greatCircleDistance(way.getNode(m + 1).getCoor());
					}
					if (taggedLength != null) {
						segmentLength = taggedLength * segmentLength / way.getLength();
					}

					// only create link, if both nodes were found, node could be null, since
					// nodes outside a layer were dropped
					Id<org.matsim.api.core.v01.network.Node> fromId = Id.create(NodeConversionRules.getId(nodeFrom), org.matsim.api.core.v01.network.Node.class);
					Id<org.matsim.api.core.v01.network.Node> toId = Id.create(NodeConversionRules.getId(nodeTo), org.matsim.api.core.v01.network.Node.class);
					if (scenario.getNetwork().getNodes().get(fromId) != null && scenario.getNetwork().getNodes().get(toId) != null) {
						if (forward) {
							String id = LinkConversionRules.getId(way, increment, false);
							String origId = LinkConversionRules.getOrigId(way, id, false);
							Link l = scenario.getNetwork().getFactory().createLink(Id.create(id, Link.class), scenario.getNetwork().getNodes().get(fromId), scenario.getNetwork().getNodes().get(toId));
							l.setLength(segmentLength);
							l.setFreespeed(freespeed);
							l.setCapacity(capacity);
							l.setNumberOfLanes(nofLanesPerDirection);
							l.setAllowedModes(modes);
							((LinkImpl) l).setOrigId(origId);
							scenario.getNetwork().addLink(l);
							link2Segments.put(l, segs);
							links.add(l);
						}
						if (backward) {
							String id = LinkConversionRules.getId(way, increment, true);
							String origId = LinkConversionRules.getOrigId(way, id, true);
							Link l = scenario.getNetwork().getFactory().createLink(Id.create(id, Link.class), scenario.getNetwork().getNodes().get(toId), scenario.getNetwork().getNodes().get(fromId));
							l.setLength(segmentLength);
							l.setFreespeed(freespeed);
							l.setCapacity(capacity);
							l.setNumberOfLanes(nofLanesPerDirection);
							l.setAllowedModes(modes);
							((LinkImpl) l).setOrigId(origId);
							scenario.getNetwork().addLink(l);
							link2Segments.put(l, segs);
							links.add(l);
						}
					}
					increment++;
				}
				way2Links.put(way, links);
			}
		}

		@Override
		public void visit(Node node) {
			if (visited.add(node)) {
				scenario.getNetwork().removeNode(Id.create(NodeConversionRules.getId(node), org.matsim.api.core.v01.network.Node.class));
				if (scenario.getConfig().transit().isUseTransit()) {
					TransitStopFacility transitStopFacility = scenario.getTransitSchedule().getFacilities()
							.get(Id.create(String.valueOf(node.getUniqueId()), TransitStopFacility.class));
					if (transitStopFacility != null) {
						scenario.getTransitSchedule().removeStopFacility(transitStopFacility);
					}
				}
				if (node.evaluateCondition(relevantNodeMatch)) {
					EastNorth eN = Main.getProjection().latlon2eastNorth(node.getCoor());
					NodeImpl matsimNode = (NodeImpl) scenario
							.getNetwork()
							.getFactory()
							.createNode(Id.create(NodeConversionRules.getId(node), org.matsim.api.core.v01.network.Node.class),
									new Coord(eN.getX(), eN.getY()));
					matsimNode.setOrigId(NodeConversionRules.getOrigId(node));
					scenario.getNetwork().addNode(matsimNode);
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
				if (scenario.getConfig().transit().isUseTransit()) {
					TransitStopFacility transitStopFacility = scenario.getTransitSchedule().getFacilities()
							.get(Id.create(String.valueOf(way.getUniqueId()), TransitStopFacility.class));
					if (transitStopFacility != null) {
						scenario.getTransitSchedule().removeStopFacility(transitStopFacility);
					}
				}
				if (isUsableAndNotRemoved(way)) {
					convertWay(way);
				}
			}
		}

		@Override
		public void visit(Relation relation) {
			if (visited.add(relation)) {
				if (scenario.getConfig().transit().isUseTransit()) {
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
					TransitStopFacility stop = stopRelation2TransitStop.remove(relation);
					if (stop != null) {
						scenario.getTransitSchedule().removeStopFacility(stop);
					}
					createTransitStopFacility(relation);
				}
			}
		}

		private EditableTransitRoute createTransitRoute(Relation relation, EditableTransitRoute oldRoute) {
			if (!relation.isDeleted() && relation.hasTag("type", "route")) {
				TransitLine line = findOrCreateTransitLine(relation);
				if (line != null) {
					EditableTransitRoute newRoute;
					if (oldRoute == null) {
						Id<TransitRoute> routeId = Id.create(relation.getUniqueId(), TransitRoute.class);
						newRoute = new EditableTransitRoute(routeId);
					} else {
						// Edit the previous object in place.
						newRoute = oldRoute;
						newRoute.setDeleted(false);
					}
					if (!Main.pref.getBoolean("matsim_transit_lite")) {
						NetworkRoute networkRoute = determineNetworkRoute(relation);
						newRoute.setRoute(networkRoute);
					}
					newRoute.setTransportMode(relation.get("route"));
					newRoute.getStops().clear();
					newRoute.getStops().addAll(createTransitRouteStops(relation));
					newRoute.setDescription(relation.get("route"));
					newRoute.setRealId(relation.get("ref") != null ? Id.create(relation.get("ref"), TransitRoute.class) : newRoute.getId());
					return newRoute;
				}
			}
			return null; // not a route
		}

		private List<TransitRouteStop> createTransitRouteStops(Relation relation) {
			List<TransitRouteStop> routeStops = new ArrayList<>();
			for (RelationMember member : relation.getMembers()) {
				// can be platforms and stops. we can handle both,
				// but of course we only create one facility, even if both are present.
				// the association between the two is handled by a stop area relation,
				// not by the two of them both being in this route.
				TransitStopFacility facility = findOrCreateStopFacility(member);
				if (facility != null && (routeStops.isEmpty() || facility != routeStops.get(routeStops.size()-1).getStopFacility())) {
					routeStops.add(scenario.getTransitSchedule().getFactory().createTransitRouteStop(facility, 0, 0));
				}
			}
			return routeStops;
		}

		private TransitStopFacility findOrCreateStopFacility(RelationMember member) {
			for (OsmPrimitive referrer : member.getMember().getReferrers()) {
				if (referrer instanceof Relation) {
					TransitStopFacility facility = stopRelation2TransitStop.get(referrer);
					if (facility != null) {
						return facility;
					}
					createTransitStopFacility((Relation) referrer);
					facility = stopRelation2TransitStop.get(referrer);
					if (facility != null) {
						return facility;
					}
				}
			}
			return null; // not a transit stop facility
		}

		private void createTransitStopFacility(Relation relation) {
			if (relation.hasTag("type", "public_transport") && relation.hasTag("public_transport", "stop_area")) {
				EastNorth eN = decidePlatformLocation(relation);
				if (eN != null) {
					Id<Link> linkId = determineExplicitMatsimLinkId(relation);
					EditableTransitStopFacility stop = (EditableTransitStopFacility) scenario.getTransitSchedule().getFactory()
							.createTransitStopFacility(Id.create(String.valueOf(relation.getUniqueId()), TransitStopFacility.class), new Coord(eN.getX(), eN.getY()), true);
					stop.setLinkId(linkId);
					Node stopPosition = determineStopPositionOsmNode(relation);
					if (stopPosition != null) {
						Id<org.matsim.api.core.v01.network.Node> nodeId = Id.createNodeId(NodeConversionRules.getId(stopPosition));
						if (scenario.getNetwork().getNodes().containsKey(nodeId)) {
							stop.setNodeId(nodeId);
						}
					}
					Id<TransitStopFacility> origId;
					if (relation.hasKey("ref")) {
						origId = Id.create(relation.get("ref"), TransitStopFacility.class);
					} else {
						origId = stop.getId();
					}
					if (relation.hasKey("name")) {
						stop.setName(relation.get("name"));
					}
					stop.setOrigId(Id.create(origId, TransitStopFacility.class));
					scenario.getTransitSchedule().addStopFacility(stop);
					stopRelation2TransitStop.put(relation, stop);
				}
			}
		}

		private Node determineStopPositionOsmNode(Relation relation) {
			Node stopPosition = null;
			for (RelationMember member : relation.getMembers()) {
				if (member.hasRole("stop") && member.isNode()) {
					stopPosition = member.getNode();
				}
			}
			return stopPosition;
		}

		private Id<Link> determineExplicitMatsimLinkId(Relation relation) {
			for (RelationMember member : relation.getMembers()) {
				if (member.hasRole("matsim:link") && member.isWay()) {
					Way way = member.getWay();
					List<Link> links = way2Links.get(way);
					if (links != null && !links.isEmpty()) {
						return links.get(links.size() - 1).getId();
					}
				}
			}
			return null;
		}

		private EastNorth decidePlatformLocation(Relation relation) {
			List<Node> nodes = new ArrayList<>();
			for (RelationMember member : relation.getMembers()) {
				if (member.hasRole("platform") || member.getMember().hasTag("public_transport", "platform")
						|| member.hasRole("stop")) {
					if (member.isWay()) {
						nodes.addAll(member.getWay().getNodes());
					} else if (member.isNode()) {
						nodes.add(member.getNode());
					}
				}
			}
			Iterator<Node> iterator = nodes.iterator();
			while (iterator.hasNext()) {
				Node next = iterator.next();
				if (! next.isLatLonKnown()) {
					iterator.remove();
				}
			}
			if (nodes.size() > 2) {
				return Geometry.getCenter(nodes);
			} else if (nodes.size() == 2) {
				Node node0 = nodes.get(0);
				Node node1 = nodes.get(1);
				return node0.getEastNorth().getCenter(node1.getEastNorth());
			} else if (nodes.size() == 1) {
				return nodes.get(0).getEastNorth();
			} else {
				return null;
			}
		}

		private NetworkRoute determineNetworkRoute(Relation relation) {
			List<Id<Link>> links = new ArrayList<>();
			List<RelationMember> members = relation.getMembers();
			if (!members.isEmpty()) { // WayConnectionTypeCalculator
				// will crash otherwise
				WayConnectionTypeCalculator calc = new WayConnectionTypeCalculator();
				List<WayConnectionType> connections = calc.updateLinks(members);
				for (int i=0; i<members.size(); i++) {
					RelationMember member = members.get(i);
					if (member.isWay()) {
						Way way = member.getWay();
						List<Link> wayLinks = way2Links.get(way);
						if (wayLinks != null) {
							wayLinks = new ArrayList<>(wayLinks);
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
			}
			if (links.isEmpty()) {
				return null;
			} else {
				return RouteUtils.createNetworkRoute(links, scenario.getNetwork());
			}
		}
	}


	// JOSM does not set a primitive to not usable when it is hard-deleted (i.e.
	// not set to deleted).
	// But it sets the dataSet to null when it is hard-deleted, so we
	// additionally check for that.
	private boolean isUsableAndNotRemoved(OsmPrimitive osmPrimitive) {
		return osmPrimitive.isUsable() && osmPrimitive.getDataSet() != null;
	}

	EditableTransitRoute findRoute(OsmPrimitive maybeRelation) {
		if (maybeRelation instanceof Relation && scenario.getConfig().transit().isUseTransit()) {
			for (EditableTransitLine editableTransitLine : scenario.getTransitSchedule().getEditableTransitLines().values()) {
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
			if (primitive instanceof Relation && primitive.hasTag("type", "route_master")) {
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

		Relation maybeLineRelation = ((Relation) data.getPrimitiveById(Long.parseLong(transitLineId.toString()), OsmPrimitiveType.RELATION));

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

}
