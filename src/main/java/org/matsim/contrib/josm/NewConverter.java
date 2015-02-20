package org.matsim.contrib.josm;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.LinkImpl;
import org.matsim.core.network.NodeImpl;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;

import java.util.*;

class NewConverter {

	final static String TAG_LANES = "lanes";
	final static String TAG_HIGHWAY = "highway";
	final static String TAG_RAILWAY = "railway";
	final static String TAG_MAXSPEED = "maxspeed";
	final static String TAG_JUNCTION = "junction";
	final static String TAG_ONEWAY = "oneway";

    static Set<String> determineModes(Way way) {
        Set<String> modes = new HashSet<>();
        if (way.getKeys().containsKey("modes")) {
            Set<String> tempModes = new HashSet<>();
            String tempArray[] = way.getKeys().get("modes").split(";");
            Collections.addAll(tempModes, tempArray);
            if (tempModes.size() != 0) {
                modes.clear();
                modes.addAll(tempModes);
            }
        }
        if (modes.isEmpty()) {
            if (way.getKeys().containsKey(TAG_RAILWAY)) {
                modes.add(TransportMode.pt);
            }
            if (way.getKeys().containsKey(TAG_HIGHWAY)) {
                modes.add(TransportMode.car);
            }
        }
        return modes;
    }

	// create or update matsim node
	static void checkNode(Network network, Node node) {
		Id<org.matsim.api.core.v01.network.Node> nodeId = Id.create(
				node.getUniqueId(), org.matsim.api.core.v01.network.Node.class);
		if (!node.isIncomplete()) {
            EastNorth eastNorth = node.getEastNorth();
            NodeImpl matsimNode = (NodeImpl) network.getNodes().get(nodeId);
            if (matsimNode == null) {
                matsimNode = (NodeImpl) network
                        .getFactory()
                        .createNode(
                                Id.create(
                                        node.getUniqueId(),
                                        org.matsim.api.core.v01.network.Node.class),
                                new CoordImpl(eastNorth.getX(), eastNorth.getY()));
                network.addNode(matsimNode);
            } else {
				Coord coord = new CoordImpl(eastNorth.getX(), eastNorth.getY());
				matsimNode.setCoord(coord);
			}
            if (node.hasKey(ImportTask.NODE_TAG_ID)) {
                matsimNode.setOrigId(node.get(ImportTask.NODE_TAG_ID));
            } else {
                matsimNode.setOrigId(String.valueOf(node.getUniqueId()));
            }
		}
	}

	// creates links between given nodes along the respective WaySegments.
	// adapted from original OsmNetworkReader
	static List<Link> createLink(final Network network,
                                 final Way way, final Node fromNode,
                                 final Node toNode, double length, long increment, boolean oneway,
                                 boolean onewayReverse, Double freespeed, Double capacity,
                                 Double nofLanes, Set<String> modes) {

		// only create link, if both nodes were found, node could be null, since
		// nodes outside a layer were dropped
		List<Link> links = new ArrayList<>();
		Id<org.matsim.api.core.v01.network.Node> fromId = Id.create(
				fromNode.getUniqueId(),
				org.matsim.api.core.v01.network.Node.class);
		Id<org.matsim.api.core.v01.network.Node> toId = Id.create(
				toNode.getUniqueId(),
				org.matsim.api.core.v01.network.Node.class);
		if (network.getNodes().get(fromId) != null && network.getNodes().get(toId) != null) {
			String id = String.valueOf(way.getUniqueId()) + "_" + increment;
			String origId;

			if (way.hasKey(ImportTask.WAY_TAG_ID)) {
				origId = way.get(ImportTask.WAY_TAG_ID);
			} else {
                origId = id;
            }

			if (!onewayReverse) {
				Link l = network.getFactory().createLink(
						Id.create(id, Link.class),
						network.getNodes().get(fromId),
						network.getNodes().get(toId));
				l.setLength(length);
				l.setFreespeed(freespeed);
				l.setCapacity(capacity);
				l.setNumberOfLanes(nofLanes);
				l.setAllowedModes(modes);
				((LinkImpl) l).setOrigId(origId);
				network.addLink(l);
				links.add(l);
			}
			if (!oneway) {
				Link l = network.getFactory().createLink(
						Id.create(id + "_r", Link.class),
						network.getNodes().get(toId),
						network.getNodes().get(fromId));
				l.setLength(length);
				l.setFreespeed(freespeed);
				l.setCapacity(capacity);
				l.setNumberOfLanes(nofLanes);
				l.setAllowedModes(modes);
				((LinkImpl) l).setOrigId(origId + "_r");
				network.addLink(l);
				links.add(l);
			}
		}
		return links;
	}

    static Id<TransitLine> getTransitLineId(Relation relation) {
        for (OsmPrimitive primitive : relation.getReferrers()) {
            if (primitive instanceof Relation && primitive.hasTag("type", "route_master")) {
                return Id.create(primitive.getUniqueId(), TransitLine.class);
            }
        }
        // no enclosing transit line; use route id as line id;
        return Id.create(relation.getUniqueId(), TransitLine.class);
    }

    // checks for used MATSim tag scheme
	static boolean meetsMatsimReq(Map<String, String> keys) {
		if (!keys.containsKey("capacity"))
			return false;
		if (!keys.containsKey("freespeed"))
			return false;
		if (!keys.containsKey("permlanes"))
			return false;
		if (!keys.containsKey("modes"))
			return false;
		return true;
	}

	static Double parseDoubleIfPossible(String string) {
		try {
			return Double.parseDouble(string);
		} catch (NumberFormatException e) {
			return null;
		}
	}

}
