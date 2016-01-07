package org.matsim.contrib.josm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.LinkImpl;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;

class NewConverter {

	final static String TAG_HIGHWAY = "highway";
	final static String TAG_RAILWAY = "railway";

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

	// creates links between given nodes along the respective WaySegments.
	// adapted from original OsmNetworkReader
	static List<Link> createLink(final Network network, final Way way, final Node fromNode, final Node toNode, double length, long increment,
								 boolean forward, boolean backward, Double freespeed, Double capacity, Double nofLanes, Set<String> modes) {

		// only create link, if both nodes were found, node could be null, since
		// nodes outside a layer were dropped
		List<Link> links = new ArrayList<>();
		Id<org.matsim.api.core.v01.network.Node> fromId = Id.create(fromNode.getUniqueId(), org.matsim.api.core.v01.network.Node.class);
		Id<org.matsim.api.core.v01.network.Node> toId = Id.create(toNode.getUniqueId(), org.matsim.api.core.v01.network.Node.class);
		if (network.getNodes().get(fromId) != null && network.getNodes().get(toId) != null) {
			String id = String.valueOf(way.getUniqueId()) + "_" + increment;
			String origId;

			if (way.hasKey(ImportTask.WAY_TAG_ID)) {
				origId = way.get(ImportTask.WAY_TAG_ID);
			} else {
				origId = id;
			}

			if (forward) {
				Link l = network.getFactory().createLink(Id.create(id, Link.class), network.getNodes().get(fromId), network.getNodes().get(toId));
				l.setLength(length);
				l.setFreespeed(freespeed);
				l.setCapacity(capacity);
				l.setNumberOfLanes(nofLanes);
				l.setAllowedModes(modes);
				((LinkImpl) l).setOrigId(origId);
				network.addLink(l);
				links.add(l);
			}
			if (backward) {
				Link l = network.getFactory().createLink(Id.create(id + "_r", Link.class), network.getNodes().get(toId),
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
