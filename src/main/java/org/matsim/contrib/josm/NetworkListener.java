package org.matsim.contrib.josm;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.network.NodeImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.pt.transitSchedule.api.*;
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
class NetworkListener implements DataSetListener {

	private final Logger log = Logger.getLogger(NetworkListener.class);

	private final Scenario scenario;

	private final Map<Way, List<Link>> way2Links;
	private final Map<Link, List<WaySegment>> link2Segments;
	private final Map<Relation, TransitRoute> relation2Route;

	public NetworkListener(Scenario scenario, Map<Way, List<Link>> way2Links,
                           Map<Link, List<WaySegment>> link2Segments,
                           Map<Relation, TransitRoute> relation2Route)
			throws IllegalArgumentException {
		this.scenario = scenario;
		this.way2Links = way2Links;
		this.link2Segments = link2Segments;
		this.relation2Route = relation2Route;
		log.debug("Listener initialized");
	}

    void visitAll(DataSet data) {
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
    }

    @Override
	public void dataChanged(DataChangedEvent dataChangedEvent) {
		log.debug("Data changed. ");
        visitAll(dataChangedEvent.getDataset());
	}

	@Override
	// convert all referred elements of the moved node
	public void nodeMoved(NodeMovedEvent moved) {
		log.debug("Node(s) moved.");
        MyAggregatePrimitivesVisitor aggregatePrimitivesVisitor = new MyAggregatePrimitivesVisitor();
        aggregatePrimitivesVisitor.visit(moved.getNode());
    }

    @Override
	public void otherDatasetChange(AbstractDatasetChangedEvent arg0) {
		log.debug("Other dataset change. " + arg0.getType());
        visitAll(arg0.getDataset());
	}

	@Override
	// convert added primitive as well as the ones connected to it
	public void primitivesAdded(PrimitivesAddedEvent added) {
        MyAggregatePrimitivesVisitor aggregatePrimitivesVisitor = new MyAggregatePrimitivesVisitor();
        for (OsmPrimitive primitive : added.getPrimitives()) {
			log.info("Primitive added. " + primitive.getType() + " " + primitive.getUniqueId());
			if (primitive instanceof Way) {
                aggregatePrimitivesVisitor.visit((Way) primitive);
			} else if (primitive instanceof Relation) {
                aggregatePrimitivesVisitor.visit((Relation) primitive);
			} else if (primitive instanceof org.openstreetmap.josm.data.osm.Node) {
                aggregatePrimitivesVisitor.visit((org.openstreetmap.josm.data.osm.Node) primitive);
			}
		}
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
	}

	@Override
	// convert affected relation
	public void relationMembersChanged(RelationMembersChangedEvent arg0) {
		log.debug("Relation member changed " + arg0.getType());
        MyAggregatePrimitivesVisitor aggregatePrimitivesVisitor = new MyAggregatePrimitivesVisitor();
        aggregatePrimitivesVisitor.visit(arg0.getRelation());
    }

	@Override
	// convert affected elements and other connected elements
	public void tagsChanged(TagsChangedEvent changed) {
        MyAggregatePrimitivesVisitor aggregatePrimitivesVisitor = new MyAggregatePrimitivesVisitor();
        log.debug("Tags changed " + changed.getType() + " "
				+ changed.getPrimitive().getType() + " "
				+ changed.getPrimitive().getUniqueId());
		for (OsmPrimitive primitive : changed.getPrimitives()) {
			if (primitive instanceof Way) {
                aggregatePrimitivesVisitor.visit((Way) primitive);
			} else if (primitive instanceof org.openstreetmap.josm.data.osm.Node) {
                aggregatePrimitivesVisitor.visit((org.openstreetmap.josm.data.osm.Node) primitive);
			} else if (primitive instanceof Relation) {
                aggregatePrimitivesVisitor.visit((Relation) primitive);
			}
		}
    }

	@Override
	// convert affected elements and other connected elements
	public void wayNodesChanged(WayNodesChangedEvent changed) {
        MyAggregatePrimitivesVisitor aggregatePrimitivesVisitor = new MyAggregatePrimitivesVisitor();
        log.debug("Way Nodes changed " + changed.getType() + " "
				+ changed.getChangedWay().getType() + " "
				+ changed.getChangedWay().getUniqueId());
        aggregatePrimitivesVisitor.visit(changed.getChangedWay());
	}

    private void searchAndRemoveRoute(TransitRoute route) {
        // We do not know what line the route is in, so we have to search for it.
        for (TransitLine line : scenario.getTransitSchedule().getTransitLines().values()) {
            boolean removed = line.removeRoute(route);
            if (removed) {
                if (line.getRoutes().isEmpty()) {
                    // We do not create lines independently for now so we have to remove empty lines
                    scenario.getTransitSchedule().removeTransitLine(line);
                    return;
                }
            }
        }
    }

    class MyAggregatePrimitivesVisitor extends AbstractVisitor {

        final Collection<OsmPrimitive> visited = new HashSet<>();

        @Override
        public void visit(Node node) {
            if (visited.add(node)) {
                log.debug("Visiting node " + node.getUniqueId() + " " + node.getName());
                if (node.isDeleted()) {
                    Id<org.matsim.api.core.v01.network.Node> id = Id.create(node.getUniqueId(), org.matsim.api.core.v01.network.Node.class);
                    if (scenario.getNetwork().getNodes().containsKey(id)) {
                        NodeImpl matsimNode = (NodeImpl) scenario.getNetwork().getNodes().get(id);
                        log.debug("MATSim Node removed. " + matsimNode.getOrigId());
                        scenario.getNetwork().removeNode(matsimNode.getId());
                    }
                } else {
                    NewConverter.checkNode(scenario.getNetwork(), node);
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
                        log.debug(removedLink + " removed.");
                    }
                }
                if (!way.isDeleted()) {
                    NewConverter.convertWay(way, scenario.getNetwork(), way2Links, link2Segments);
                }
                for (OsmPrimitive primitive : way.getReferrers()) {
                    primitive.accept(this);
                }
                log.info("Number of links: " + scenario.getNetwork().getLinks().size());
            }

        }

        @Override
        public void visit(Relation relation) {
            if (visited.add(relation)) {
                // convert Relation, remove previous references in the MATSim data
                TransitRoute route = relation2Route.remove(relation);
                if (route != null) {
                    searchAndRemoveRoute(route);
                }
                if (scenario.getConfig().scenario().isUseTransit()) {
                    Id<TransitStopFacility> transitStopFacilityId = Id.create(relation.getUniqueId(), TransitStopFacility.class);
                    if (scenario.getTransitSchedule().getFacilities().containsKey(transitStopFacilityId)) {
                        scenario.getTransitSchedule().removeStopFacility(scenario.getTransitSchedule().getFacilities().get(transitStopFacilityId));
                    }
                }
                if (!relation.isDeleted()) {
                    if (relation.hasTag("type", "route")) {
                        createTransitRoute(relation);
                    }
                    if (relation.hasTag("matsim", "stop_relation")) {
                        createStopFacility(relation);
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
                    if (links != null) {
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
            log.debug("converting route relation" + relation.getUniqueId() + " " + relation.getName());
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
                visit(way);
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

}
