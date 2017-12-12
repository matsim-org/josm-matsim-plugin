package org.matsim.contrib.josm.model;

import javafx.beans.property.ReadOnlyMapProperty;
import javafx.beans.property.ReadOnlyMapWrapper;
import javafx.collections.FXCollections;
import org.matsim.api.core.v01.Coord;
import org.matsim.contrib.josm.gui.Preferences;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.data.osm.event.*;
import org.openstreetmap.josm.data.osm.visitor.OsmPrimitiveVisitor;
import org.openstreetmap.josm.spi.preferences.IPreferences;

import java.util.*;

/**
 * Listens to changes in the dataset and their effects on the Network
 *
 *
 */
public class NetworkModel {

	private class NetworkModelDataSetListener implements DataSetListener {

		@Override
		public void dataChanged(DataChangedEvent dataChangedEvent) {
			visitAll();
			fireNotifyDataChanged();
		}

		@Override
		// convert all referred elements of the moved node
		public void nodeMoved(NodeMovedEvent moved) {
			AggregatePrimitives aggregatePrimitivesVisitor = new AggregatePrimitives();
			aggregatePrimitivesVisitor.visit(moved.getNode());
			aggregatePrimitivesVisitor.finished();
			fireNotifyDataChanged();
		}

		@Override
		public void otherDatasetChange(AbstractDatasetChangedEvent arg0) {
		}

		@Override
		// convert added primitive as well as the ones connected to it
		public void primitivesAdded(PrimitivesAddedEvent added) {
			AggregatePrimitives aggregatePrimitivesVisitor = new AggregatePrimitives();
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
			AggregatePrimitives aggregatePrimitivesVisitor = new AggregatePrimitives();
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
			AggregatePrimitives aggregatePrimitivesVisitor = new AggregatePrimitives();
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
			AggregatePrimitives aggregatePrimitivesVisitor = new AggregatePrimitives();
			for (OsmPrimitive primitive : changed.getPrimitives()) {
				if (primitive instanceof Way) {
					Way way = (Way) primitive;
					aggregatePrimitivesVisitor.visit(way);
					for (Node node : way.getNodes()) {
						aggregatePrimitivesVisitor.visit(node);
					}
					aggregatePrimitivesVisitor.visit(way);
					List<MLink> links = way2Links.get(way);
					if (links != null) {
						for (MLink link : links) {
							aggregatePrimitivesVisitor.visit(link.getFromNode().getOsmNode());
							aggregatePrimitivesVisitor.visit(link.getToNode().getOsmNode());
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
			AggregatePrimitives aggregatePrimitivesVisitor = new AggregatePrimitives();
			for (Node node : changed.getChangedWay().getNodes()) {
				aggregatePrimitivesVisitor.visit(node);
			}
			List<MLink> links = way2Links.get(changed.getChangedWay());
			if (links != null) {
				for (MLink link : links) {
					aggregatePrimitivesVisitor.visit(link.getFromNode().getOsmNode());
					aggregatePrimitivesVisitor.visit(link.getToNode().getOsmNode());
				}
			}
			aggregatePrimitivesVisitor.visit((changed.getChangedWay()));
			aggregatePrimitivesVisitor.finished();
			fireNotifyDataChanged();
		}

	}

	final static String TAG_HIGHWAY = "highway";
	final static String TAG_RAILWAY = "railway";

	private ReadOnlyMapWrapper<Node, MNode> nodes = new ReadOnlyMapWrapper<>(FXCollections.observableHashMap());
	private final Map<Way, List<MLink>> way2Links;
	private ReadOnlyMapWrapper<Relation, StopArea> stopAreas = new ReadOnlyMapWrapper<>(FXCollections.observableHashMap());
	private ReadOnlyMapWrapper<Relation, Line> lines = new ReadOnlyMapWrapper<>(FXCollections.observableHashMap());
	private ReadOnlyMapWrapper<Relation, Route> routes = new ReadOnlyMapWrapper<>(FXCollections.observableHashMap());
	private DataSet data;
	private Collection<ScenarioDataChangedListener> listeners = new ArrayList<>();

	public static NetworkModel createNetworkModel(DataSet data) {
		Config config = ConfigUtils.createConfig();
		config.transit().setUseTransit(true);
		return NetworkModel.createNetworkModel(data, new HashMap<>());
	}

	public static NetworkModel createNetworkModel(DataSet data, Map<Way, List<MLink>> way2Links) {
		return new NetworkModel(data, org.openstreetmap.josm.spi.preferences.Config.getPref(), way2Links);
	}

	public interface ScenarioDataChangedListener {
		void notifyDataChanged();
	}

	public void removeListener(ScenarioDataChangedListener listener) {
		listeners.remove(listener);
	}

	void fireNotifyDataChanged() {
		for (ScenarioDataChangedListener listener : listeners) {
			listener.notifyDataChanged();
		}
	}

	public void addListener(ScenarioDataChangedListener listener) {
		listeners.add(listener);
	}

	private NetworkModel(DataSet data, IPreferences prefs, Map<Way, List<MLink>> way2Links) {
		this.data = data;
		this.data.addDataSetListener(new NetworkModelDataSetListener());
		prefs.addPreferenceChangeListener(e -> {
			if (e.getKey().equalsIgnoreCase("matsim_keepPaths")
					|| e.getKey().equalsIgnoreCase("matsim_filterActive")
					|| e.getKey().equalsIgnoreCase("matsim_filter_hierarchy")
					|| e.getKey().equalsIgnoreCase("matsim_transit_lite")
					|| e.getKey().startsWith("matsim_convertDefaults")) {
				visitAll();
			}
			fireNotifyDataChanged();
		});
		Main.addProjectionChangeListener((oldValue, newValue) -> {
			visitAll();
			fireNotifyDataChanged();
		});
		this.way2Links = way2Links;
	}

	public void visitAll() {
		Convert visitor = new Convert();
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

	class AggregatePrimitives implements OsmPrimitiveVisitor {

		Set<OsmPrimitive> primitives = new HashSet<>();

		@Override
		public void visit(Node node) {
			primitives.add(node);
			// When a Node was touched, we need to look at ways (because their
			// length may change)
			// and at relations (because it may be a transit stop)
			// which contain it.
			visitReferrers(node);
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
			visitReferrers(way);
		}

		private void visitReferrers(OsmPrimitive primitive) {
			// Annoyingly, JOSM removes the dataSet property from primitives
			// before calling this listener when
			// a primitive is "hard" deleted (not flagged as deleted).
			// So we have to check for this before asking for its referrers.
			if (primitive.getDataSet() != null) {
				for (OsmPrimitive referrer : primitive.getReferrers()) {
					referrer.accept(this);
				}
			}
		}

		@Override
		public void visit(Relation relation) {
			if (Preferences.isSupportTransit() && relation.hasTag("type", "route_master")) {
				for (OsmPrimitive osmPrimitive : relation.getMemberPrimitives()) {
					osmPrimitive.accept(this);
				}
			}
			primitives.add(relation);
		}

		void finished() {
			Convert visitor = new Convert();
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

	private void searchAndRemoveRoute(Route route) {
		// We do not know what line the route is in, so we have to search for
		// it.
		for (Line line : lines.values()) {
			line.removeRoute(route);
		}
		routes.remove(route.getRelation());
	}

	class Convert implements OsmPrimitiveVisitor {

		final Collection<OsmPrimitive> visited = new HashSet<>();

		void convertWay(Way way) {
			final OsmConvertDefaults.OsmWayDefaults defaults = LinkConversionRules.getWayDefaults(way);

			final boolean forward = LinkConversionRules.isForward(way, defaults);
			final boolean backward = LinkConversionRules.isBackward(way, defaults);
			final Double freespeed = LinkConversionRules.getFreespeed(way, defaults);
			final Double nofLanesPerDirection = LinkConversionRules.getLanesPerDirection(way, defaults, forward, backward);
			final Double capacity = LinkConversionRules.getCapacity(way, defaults, nofLanesPerDirection);
			final Set<String> modes = LinkConversionRules.getModes(way, defaults);
			final String highwayType = LinkConversionRules.getType(way, defaults);

			final Double taggedLength = LinkConversionRules.getTaggedLength(way);

			if (capacity != null && freespeed != null && nofLanesPerDirection != null && modes != null) {
				List<Node> nodeOrder = new ArrayList<>();
				for (Node current : way.getNodes()) {
					if (nodes().containsKey(current)) {
						nodeOrder.add(current);
					}
				}
				List<MLink> links = new ArrayList<>();
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
					if (nodes().get(nodeFrom) != null && nodes().get(nodeTo) != null) {
						if (forward) {
							String id = LinkConversionRules.getId(way, increment, false);
							String origId = LinkConversionRules.getOrigId(way, id, false);
							MLink l = new MLink(nodes.get(nodeFrom), nodes.get(nodeTo));
							l.setLength(segmentLength);
							l.setFreespeed(freespeed);
							l.setCapacity(capacity);
							l.setNumberOfLanes(nofLanesPerDirection);
							l.setAllowedModes(modes);
							l.setOrigId(origId);
							l.setSegments(segs);
							l.setType(highwayType);
							links.add(l);
						}
						if (backward) {
							String id = LinkConversionRules.getId(way, increment, true);
							String origId = LinkConversionRules.getOrigId(way, id, true);
							MLink l = new MLink(nodes.get(nodeTo), nodes.get(nodeFrom));
							l.setLength(segmentLength);
							l.setFreespeed(freespeed);
							l.setCapacity(capacity);
							l.setNumberOfLanes(nofLanesPerDirection);
							l.setAllowedModes(modes);
							l.setOrigId(origId);
							l.setSegments(segs);
							l.setType(highwayType);
							l.setReverseWayDirection(true);
							links.add(l);
						}
					}
					increment++;
				}
				way2Links.put(way, links);
			}
		}

		private boolean isExplicitelyMatsimTagged(Way way) {
			return way.get(LinkConversionRules.ID) != null;
		}

		private boolean isExplicitelyMatsimTagged(Node node) {
			return node.get(NodeConversionRules.ID) != null;
		}

		@Override
		public void visit(Node node) {
			if (visited.add(node)) {
				nodes.remove(node);
				if (isRelevant(node)) {
					EastNorth eN = Main.getProjection().latlon2eastNorth(node.getCoor());
					MNode matsimNode = new MNode(node, new Coord(eN.getX(), eN.getY()));
					matsimNode.setOrigId(NodeConversionRules.getOrigId(node));
					nodes.put(node, matsimNode);
				}
			}
		}

		private boolean isRelevant(Node node) {
			if (isUsableAndNotRemoved(node)) {
				Way junctionWay = null;
				for (Way way : OsmPrimitive.getFilteredList(node.getReferrers(), Way.class)) {
					if (isUsableAndNotRemoved(way) && LinkConversionRules.isMatsimWay(way)) {
						if (Preferences.isKeepPaths() || way.isFirstLastNode(node) || junctionWay != null || node.hasTag("public_transport", "stop_position")) {
							return true;
						}
						junctionWay = way;
					}
				}
			}
			return false;
		}

		@Override
		public void visit(Way way) {
			if (visited.add(way)) {
				way2Links.remove(way);
				if (isUsableAndNotRemoved(way)) {
					convertWay(way);
				}
			}
		}

		@Override
		public void visit(Relation relation) {
			if (visited.add(relation)) {
				if (Preferences.isSupportTransit()) {
					Route oldRoute = findRoute(relation);
					Route newRoute = createTransitRoute(relation, oldRoute);
					if (oldRoute != null && newRoute == null) {
						oldRoute.setDeleted(true);
					} else if (oldRoute == null && newRoute != null) {
						Line tLine = findOrCreateTransitLine(relation);
						tLine.addRoute(newRoute);
						routes.put(relation, newRoute);
					} else if (oldRoute != null) {
						Line tLine = findOrCreateTransitLine(relation);
						// The line the route is assigned to might have changed,
						// so remove it and add it again.
						searchAndRemoveRoute(oldRoute);
						tLine.addRoute(newRoute);
						routes.put(relation, newRoute);
					}
					stopAreas.remove(relation);
					createTransitStopFacility(relation);
				}
			}
		}

		private Route createTransitRoute(Relation relation, Route oldRoute) {
			if (!relation.isDeleted() && relation.hasTag("type", "route") && relation.get("route") != null) {
				Line line = findOrCreateTransitLine(relation);
				if (line != null) {
					Route newRoute;
					if (oldRoute == null) {
						newRoute = new Route(relation, stopAreas, way2Links);
					} else {
						// Edit the previous object in place.
						newRoute = oldRoute;
						newRoute.setDeleted(false);
					}
					return newRoute;
				}
			}
			return null; // not a route
		}

		private void createTransitStopFacility(Relation relation) {
			if (relation.hasTag("type", "public_transport") && relation.hasTag("public_transport", "stop_area")) {
				StopArea stopArea = new StopArea(relation);
				if (stopArea.getCoord() != null) {
					stopArea.setLink(determineExplicitMatsimLink(relation));
					stopAreas.put(relation, stopArea);
				}
			}
		}

		private MLink determineExplicitMatsimLink(Relation relation) {
			for (RelationMember member : relation.getMembers()) {
				if (member.hasRole("matsim:link") && member.isWay()) {
					Way way = member.getWay();
					List<MLink> links = way2Links.get(way);
					if (links != null && !links.isEmpty()) {
						return links.get(links.size() - 1);
					}
				}
			}
			return null;
		}

	}


	// JOSM does not set a primitive to not usable when it is hard-deleted (i.e.
	// not set to deleted).
	// But it sets the dataSet to null when it is hard-deleted, so we
	// additionally check for that.
	private boolean isUsableAndNotRemoved(OsmPrimitive osmPrimitive) {
		return osmPrimitive.isUsable() && osmPrimitive.getDataSet() != null;
	}

	public Route findRoute(OsmPrimitive maybeRelation) {
		if (maybeRelation instanceof Relation) {
			for (Line line : lines.values()) {
				for (Route route : line.getRoutes()) {
					if (route.getRelation() == maybeRelation) {
						return route;
					}
				}
			}
		}
		return null;
	}

	private Line findOrCreateTransitLine(Relation route) {
		for (OsmPrimitive primitive : route.getReferrers()) {
			if (primitive instanceof Relation && primitive.hasTag("type", "route_master")) {
				for (Line line : lines.values()) {
					if (line.getRelation() == primitive) {
						return line;
					}
				}
				Line tLine = new Line((Relation) primitive);
				lines.put(tLine.getRelation(), tLine);
				return tLine;
			}
		}
		return null;
	}

	public Map<Way, List<MLink>> getWay2Links() {
		return way2Links;
	}

	public ReadOnlyMapProperty<Node, MNode> nodes() {
		return nodes.getReadOnlyProperty();
	}

	public ReadOnlyMapProperty<Relation, StopArea> stopAreas() {
		return stopAreas.getReadOnlyProperty();
	}

	public ReadOnlyMapProperty<Relation, Line> lines() {
		return lines.getReadOnlyProperty();
	}

	public ReadOnlyMapProperty<Relation, Route> routes() {
		return routes.getReadOnlyProperty();
	}

}
