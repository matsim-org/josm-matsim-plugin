package org.matsim.contrib.josm;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.network.NodeImpl;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.data.osm.event.*;
import org.openstreetmap.josm.data.osm.visitor.Visitor;
import org.openstreetmap.josm.data.validation.util.AggregatePrimitivesVisitor;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Listens to changes in the dataset and their effects on the Network
 * 
 * 
 */
class NetworkListener implements DataSetListener, Visitor {

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
        for (Node node : data.getNodes()) {
            visit(node);
        }
        for (Way way : data.getWays()) {
            visit(way);
        }
        for (Relation relation : data.getRelations()) {
            visit(relation);
        }
    }

    private void visitAll(Collection<OsmPrimitive> hull) {
        for (OsmPrimitive primitive : hull) {
            if (primitive instanceof Node) {
                visit(((Node) primitive));
            }
        }
        for (OsmPrimitive primitive : hull) {
            if (primitive instanceof Way) {
                visit(((Way) primitive));
            }
        }
        for (OsmPrimitive primitive : hull) {
            if (primitive instanceof Relation) {
                visit(((Relation) primitive));
            }
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
        AggregatePrimitivesVisitor aggregatePrimitivesVisitor = new AggregatePrimitivesVisitor();
        aggregatePrimitivesVisitor.visit(moved.getNode());
		moved.getNode().visitReferrers(aggregatePrimitivesVisitor);
        Collection<OsmPrimitive> hull = aggregatePrimitivesVisitor.visit(Collections.<OsmPrimitive>emptyList());
        visitAll(hull);
    }

    @Override
	public void otherDatasetChange(AbstractDatasetChangedEvent arg0) {
		log.debug("Other dataset change. " + arg0.getType());
        visitAll(arg0.getDataset());
	}

	@Override
	// convert added primitive as well as the ones connected to it
	public void primitivesAdded(PrimitivesAddedEvent added) {
        AggregatePrimitivesVisitor aggregatePrimitivesVisitor = new AggregatePrimitivesVisitor();
        for (OsmPrimitive primitive : added.getPrimitives()) {
			log.info("Primitive added. " + primitive.getType() + " " + primitive.getUniqueId());
			if (primitive instanceof Way) {
                aggregatePrimitivesVisitor.visit((Way) primitive);
				primitive.visitReferrers(aggregatePrimitivesVisitor);
			} else if (primitive instanceof Relation) {
                aggregatePrimitivesVisitor.visit((Relation) primitive);
			} else if (primitive instanceof org.openstreetmap.josm.data.osm.Node) {
                aggregatePrimitivesVisitor.visit((org.openstreetmap.josm.data.osm.Node) primitive);
			}
		}
        Collection<OsmPrimitive> hull = aggregatePrimitivesVisitor.visit(Collections.<OsmPrimitive>emptyList());
        visitAll(hull);
	}

	@Override
	// delete any MATSim reference to the removed element and invoke new
	// conversion of referring elements
	public void primitivesRemoved(PrimitivesRemovedEvent primitivesRemoved) {
		for (OsmPrimitive primitive : primitivesRemoved.getPrimitives()) {
			if (primitive instanceof org.openstreetmap.josm.data.osm.Node) {
                visit(((org.openstreetmap.josm.data.osm.Node) primitive));
			} else if (primitive instanceof Way) {
				visit((Way) primitive);
			} else if (primitive instanceof Relation) {
				visit((Relation) primitive);
			}
		}
	}

	@Override
	// convert affected relation
	public void relationMembersChanged(RelationMembersChangedEvent arg0) {
		log.debug("Relation member changed " + arg0.getType());
		visit(arg0.getRelation());
	}

	@Override
	// convert affected elements and other connected elements
	public void tagsChanged(TagsChangedEvent changed) {
        AggregatePrimitivesVisitor aggregatePrimitivesVisitor = new AggregatePrimitivesVisitor();
        log.debug("Tags changed " + changed.getType() + " "
				+ changed.getPrimitive().getType() + " "
				+ changed.getPrimitive().getUniqueId());
		for (OsmPrimitive primitive : changed.getPrimitives()) {
			if (primitive instanceof Way) {
                aggregatePrimitivesVisitor.visit((Way) primitive);
				primitive.visitReferrers(aggregatePrimitivesVisitor);
			} else if (primitive instanceof org.openstreetmap.josm.data.osm.Node) {
                aggregatePrimitivesVisitor.visit((org.openstreetmap.josm.data.osm.Node) primitive);
				primitive.visitReferrers(aggregatePrimitivesVisitor);
			} else if (primitive instanceof Relation) {
                aggregatePrimitivesVisitor.visit((Relation) primitive);
			}
		}
        Collection<OsmPrimitive> hull = aggregatePrimitivesVisitor.visit(Collections.<OsmPrimitive>emptyList());
        visitAll(hull);
    }

	@Override
	// convert affected elements and other connected elements
	public void wayNodesChanged(WayNodesChangedEvent changed) {
        AggregatePrimitivesVisitor aggregatePrimitivesVisitor = new AggregatePrimitivesVisitor();
        log.debug("Way Nodes changed " + changed.getType() + " "
				+ changed.getChangedWay().getType() + " "
				+ changed.getChangedWay().getUniqueId());
        aggregatePrimitivesVisitor.visit(changed.getChangedWay());
        Collection<OsmPrimitive> hull = aggregatePrimitivesVisitor.visit(Collections.<OsmPrimitive>emptyList());
        visitAll(hull);
	}

    @Override
    public void visit(org.openstreetmap.josm.data.osm.Node node) {
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
    }

	@Override
	// convert Way, remove previous references in the MATSim data
	public void visit(Way way) {
		List<Link> oldLinks = way2Links.remove(way);
		if (oldLinks != null) {
			for (Link link : oldLinks) {
				Link removedLink = scenario.getNetwork().removeLink(link.getId());
				log.debug(removedLink + " removed.");
			}
		}
		if (!way.isDeleted()) {
			NewConverter.convertWay(way, scenario.getNetwork(), way2Links, link2Segments);
		}
		log.info("Number of links: " + scenario.getNetwork().getLinks().size());
	}

	@Override
    public void visit(Relation relation) {
        // convert Relation, remove previous references in the MATSim data
        if (relation2Route.containsKey(relation)) {
            TransitRoute route = relation2Route.get(relation);
            searchAndRemoveRoute(route);
            relation2Route.remove(relation);
        }
        if (scenario.getConfig().scenario().isUseTransit()) {
            Id<TransitStopFacility> transitStopFacilityId = Id.create(relation.getUniqueId(), TransitStopFacility.class);
            if (scenario.getTransitSchedule().getFacilities().containsKey(transitStopFacilityId)) {
                scenario.getTransitSchedule().removeStopFacility(scenario.getTransitSchedule().getFacilities().get(transitStopFacilityId));
            }
        }
        if (!relation.isDeleted()) {
            NewConverter.convertTransitRouteIfItIsOne(relation, scenario, relation2Route, way2Links);
            convertTransitStopFacilityIfItIsOne(relation);
        }
    }

    private void convertTransitStopFacilityIfItIsOne(Relation relation) {
        if (relation.hasTag("matsim", "stop_relation")) {
            Id<TransitStopFacility> transitStopFacilityId = Id.create(relation.getUniqueId(), TransitStopFacility.class);
            Link link = null;
            Node platform = null;
            for (RelationMember member : relation.getMembers()) {
                if (member.isWay() && member.hasRole("link")) {
                    Way way = member.getWay();
                    List<Link> links = way2Links.get(way);
                    link = links.get(links.size() - 1);
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

    @Override
	public void visit(Changeset arg0) {

	}
}
