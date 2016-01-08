package org.matsim.contrib.josm;


import org.openstreetmap.josm.data.osm.Node;

class NodeConversionRules {

	public static final String ID = "matsim:id";

	static long getId(Node node) {
		return node.getUniqueId();
	}

	static String getOrigId(Node node) {
		String origId;
		if (node.hasKey(ID)) {
			origId = node.get(ID);
		} else {
			origId = String.valueOf(getId(node));
		}
		return origId;
	}

}
