package org.matsim.contrib.josm.model;

import org.matsim.api.core.v01.Coord;
import org.openstreetmap.josm.data.osm.Node;

public class MNode {
	private String origId;
	private Coord coord;
	private Node osmNode;

	public MNode(Node osmNode, Coord coord) {
		this.osmNode = osmNode;
		this.coord = coord;
	}

	public void setOrigId(String origId) {
		this.origId = origId;
	}

	public String getOrigId() {
		return origId;
	}

	public Coord getCoord() {
		return coord;
	}

	public Node getOsmNode() {
		return osmNode;
	}
}
