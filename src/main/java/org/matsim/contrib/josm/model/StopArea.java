package org.matsim.contrib.josm.model;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.contrib.josm.scenario.EditableTransitStopFacility;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.tools.Geometry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class StopArea extends EditableTransitStopFacility {
	private final Relation relation;

	public StopArea(Relation relation) {
		super(Id.create(relation.getUniqueId(), TransitStopFacility.class));
		setIsBlockingLane(true);
		this.relation = relation;
	}

	public Id<TransitStopFacility> getOrigId() {
		if (relation.hasKey("ref")) {
			return Id.create(relation.get("ref"), TransitStopFacility.class);
		} else {
			return getId();
		}
	}

	@Override
	public String getName() {
		if (relation.hasKey("name")) {
			return relation.get("name");
		}
		return null;
	}

	@Override
	public String getStopPostAreaId() {
		return null;
	}

	@Override
	public Coord getCoord() {
		EastNorth eN = platformLocation();
		if (eN != null) {
			return toCoord(eN);
		} else {
			return null;
		}
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

	@Override
	public Map<String, Object> getCustomAttributes() {
		return null;
	}

	@Override
	public void setName(String s) {
		throw new RuntimeException();
	}

	@Override
	public void setStopPostAreaId(String s) {
		throw new RuntimeException();
	}

	@Override
	public void setCoord(Coord coord) {
		throw new RuntimeException();
	}

	public Relation getRelation() {
		return relation;
	}
}
