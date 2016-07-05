package org.matsim.contrib.josm.model;

import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.tools.Geometry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class StopArea {
	private final Relation relation;

	private final ListProperty<Node> stopPositionOsmNodes = new SimpleListProperty<>(FXCollections.observableArrayList());
	private boolean isBlockingLane;

	private Id<Link> linkId;

	public StopArea(Relation relation) {
		this.relation = relation;
		setIsBlockingLane(true);
	}

	public String getName() {
		if (relation.hasKey("name")) {
			return relation.get("name");
		}
		return null;
	}

	public Coord getCoord() {
		EastNorth eN = platformLocation();
		if (eN != null) {
			return toCoord(eN);
		} else {
			return null;
		}
	}

	public List<Node> getStopPositionOsmNodes() {
		List<Node> stopPositions = new ArrayList<>();
		for (RelationMember member : relation.getMembers()) {
			if (member.hasRole("stop") && member.isNode()) {
				Node stopPosition = member.getNode();
				if (stopPosition != null) {
					stopPositions.add(stopPosition);
				}
			}
		}
		stopPositionOsmNodes.setAll(stopPositions);
		return stopPositionOsmNodes.get();
	}


	private Coord toCoord(EastNorth eN) {
		return new Coord(eN.getX(), eN.getY());
	}

	private EastNorth platformLocation() {
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

	public Relation getRelation() {
		return relation;
	}

	public void setIsBlockingLane(boolean isBlockingLane) {
		this.isBlockingLane = isBlockingLane;
	}

	public boolean isBlockingLane() {
		return isBlockingLane;
	}

	public Id<Link> getLinkId() {
		return linkId;
	}

	public void setLinkId(Id<Link> linkId) {
		this.linkId = linkId;
	}

	public Id<TransitStopFacility> getMatsimId() {
		return relation.get("ref") != null ? Id.create(relation.get("ref"), TransitStopFacility.class) : Id.create(relation.getUniqueId(), TransitStopFacility.class);
	}

}
