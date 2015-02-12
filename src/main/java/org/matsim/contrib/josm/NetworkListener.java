package org.matsim.contrib.josm;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.*;
import org.matsim.core.network.NodeImpl;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.event.*;
import org.openstreetmap.josm.data.osm.visitor.Visitor;

import java.util.Iterator;
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
        for (Way way : data.getWays()) {
            if (!way.isDeleted()) {
                visit(way);
            }
        }

        for (org.openstreetmap.josm.data.osm.Node node : data.getNodes()) {
            if (!node.isDeleted()) {
                visit(node);
            }
        }

        for (Relation relation : data.getRelations()) {
            if (!relation.isDeleted()) {
                visit(relation);
            }
        }
    }

    @Override
	public void dataChanged(DataChangedEvent arg0) {
		log.debug("Data changed. " + arg0.getType());
	}

	@Override
	// convert all referred elements of the moved node
	public void nodeMoved(NodeMovedEvent moved) {
		log.debug("Node(s) moved.");
		visit(moved.getNode());
		moved.getNode().visitReferrers(this);
	}

	@Override
	public void otherDatasetChange(AbstractDatasetChangedEvent arg0) {
		log.debug("Other dataset change. " + arg0.getType());
	}

	@Override
	// convert added primitive as well as the ones connected to it
	public void primitivesAdded(PrimitivesAddedEvent added) {
		for (OsmPrimitive primitive : added.getPrimitives()) {
			log.info("Primitive added. " + primitive.getType() + " "
					+ primitive.getUniqueId());
			if (primitive instanceof Way) {
				visit((Way) primitive);
				primitive.visitReferrers(this);
			} else if (primitive instanceof Relation) {
				visit((Relation) primitive);
			} else if (primitive instanceof org.openstreetmap.josm.data.osm.Node) {
				visit((org.openstreetmap.josm.data.osm.Node) primitive);
			}
		}
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
		log.debug("Tags changed " + changed.getType() + " "
				+ changed.getPrimitive().getType() + " "
				+ changed.getPrimitive().getUniqueId());
		for (OsmPrimitive primitive : changed.getPrimitives()) {
			if (primitive instanceof Way) {
				visit((Way) primitive);
				primitive.visitReferrers(this);
				for (org.openstreetmap.josm.data.osm.Node node : ((Way) primitive)
						.getNodes()) {
					if (node.isReferredByWays(2)) {
						for (OsmPrimitive prim : node.getReferrers()) {
							if (prim instanceof Way && !prim.equals(primitive)) {
								visit((Way) prim);
							}
						}
					}
				}
			} else if (primitive instanceof org.openstreetmap.josm.data.osm.Node) {
				visit((org.openstreetmap.josm.data.osm.Node)primitive);
				primitive.visitReferrers(this);
			} else if (primitive instanceof Relation) {
				visit((Relation) primitive);
			}
		}
	}

	@Override
	// convert affected elements and other connected elements
	public void wayNodesChanged(WayNodesChangedEvent changed) {
		log.debug("Way Nodes changed " + changed.getType() + " "
				+ changed.getChangedWay().getType() + " "
				+ changed.getChangedWay().getUniqueId());
		for (org.openstreetmap.josm.data.osm.Node node : changed
				.getChangedWay().getNodes()) {
			if (node.isReferredByWays(2)) {
				for (OsmPrimitive prim : node.getReferrers()) {
					if (prim instanceof Way
							&& !prim.equals(changed.getChangedWay())) {
						visit((Way) prim);
					}
				}
			}
		}
		visit(changed.getChangedWay());
	}

	@Override
	public void visit(org.openstreetmap.josm.data.osm.Node node) {
		log.debug("Visiting node " + node.getUniqueId() + " " + node.getName());
        Id<org.matsim.api.core.v01.network.Node> id = Id.create(node.getUniqueId(), org.matsim.api.core.v01.network.Node.class);
        if (scenario.getNetwork().getNodes().containsKey(id)) {
            NodeImpl matsimNode = (NodeImpl) scenario.getNetwork().getNodes().get(id);
            log.debug("MATSim Node removed. " + matsimNode.getOrigId());
            scenario.getNetwork().removeNode(matsimNode.getId());
        }
		if (node.hasTag("public_transport", "platform")) {
			Id<TransitStopFacility> stopId = Id.create(node.getUniqueId(), TransitStopFacility.class);
				TransitStopFacility stop = scenario.getTransitSchedule().getFacilities().get(stopId);
				scenario.getTransitSchedule().removeStopFacility(stop);
				log.debug("removing stop"+ node.getUniqueId() + " " + node.getName());
            if (!node.isDeleted()) {
                log.debug("converting stop"+ node.getUniqueId() + " " + node.getName());
                NewConverter.createStopIfItIsOne(node, scenario, way2Links);
            }
		}
	}

	@Override
	// convert Way, remove previous references in the MATSim data
	public void visit(Way way) {
		if (Main.main.getCurrentDataSet() != null) {
			Main.main.getCurrentDataSet().clearHighlightedWaySegments();
		}
		List<Link> oldLinks = way2Links.remove(way);
		if (oldLinks != null) {
			for (Link link : oldLinks) {
				Link removedLink = scenario.getNetwork().removeLink(
						link.getId());
				log.debug(removedLink + " removed.");
			}
		}
		if (!way.isDeleted()) {
			NewConverter.convertWay(way, scenario.getNetwork(), way2Links,
					link2Segments);
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
        if (relation.hasTag("matsim", "stop_relation")) {
            for (RelationMember member : relation.getMembers()) {
                if(member.isNode() && member.getRole().equals("platform")) {
                    visit(member.getNode());
                }
            }
        }
		if (!relation.isDeleted()) {
            NewConverter.convertTransitRouteIfItIsOne(relation, scenario, relation2Route, way2Links);
        }
	}

    private void searchAndRemoveRoute(TransitRoute route) {
        Iterator<TransitLine> i = scenario.getTransitSchedule().getTransitLines().values().iterator();
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
		// TODO Auto-generated method stub

	}
}
