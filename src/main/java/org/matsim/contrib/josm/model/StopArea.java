package org.matsim.contrib.josm.model;

import java.util.ArrayList;
import java.util.List;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.plugins.jts.JTSConverter;

import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;

public class StopArea {
	private final Relation relation;

	private final ListProperty<Node> stopPositionOsmNodes = new SimpleListProperty<>(FXCollections.observableArrayList());
	private boolean isBlockingLane;

	private MLink link;

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
		EastNorth eN = getPlatformLocation();
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

	private EastNorth getPlatformLocation() {
		List<OsmPrimitive> nodes = new ArrayList<>();
		for (RelationMember member : relation.getMembers()) {
			if(!member.getMember().isIncomplete()) {
				if (member.hasRole("platform") || member.getMember().hasTag("public_transport", "platform")
						|| member.hasRole("stop")) {
					if (member.isWay() && !member.getWay().hasIncompleteNodes()) {
						nodes.add(member.getWay());
					} else if (member.isNode() && member.getNode().isLatLonKnown()) {
						nodes.add(member.getNode());
					}
				}
			}
		}

		com.vividsolutions.jts.geom.Geometry[] geometries = nodes.stream().map(prim -> new JTSConverter().convert(prim)).toArray(size -> new com.vividsolutions.jts.geom.Geometry[size]);
		GeometryCollection geometryCollection = new GeometryCollection(geometries, new GeometryFactory());
		Point centroid = geometryCollection.getCentroid();
		if (centroid.isEmpty()) {
			return null;
		} else {
			return ProjectionRegistry.getProjection().latlon2eastNorth(new LatLon(centroid.getY(), centroid.getX()));
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

	public MLink getLink() {
		return link;
	}

	public void setLink(MLink link) {
		this.link = link;
	}

	public Id<TransitStopFacility> getMatsimId() {
		return relation.get("ref") != null ? Id.create(relation.get("ref"), TransitStopFacility.class) : Id.create(relation.getUniqueId(), TransitStopFacility.class);
	}

}
