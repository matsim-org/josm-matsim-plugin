package org.matsim.contrib.josm.model;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.openstreetmap.josm.data.osm.Relation;

import javafx.beans.property.ReadOnlyListProperty;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.collections.FXCollections;

public class Line {
	private final Relation relation;
	private ReadOnlyListWrapper<Route> routes = new ReadOnlyListWrapper<>(FXCollections.observableArrayList());


	public Line(Relation relation) {
		this.relation = relation;
	}

	public void addRoute(Route newRoute) {
		routes.add(newRoute);
	}

	public void removeRoute(Route route) {
		routes.remove(route);
	}

	public Relation getRelation() {
		return relation;
	}

	public ReadOnlyListProperty<Route> getRoutes() {
		return routes.getReadOnlyProperty();
	}

	public Id<TransitLine> getMatsimId() {
		return relation.get("ref") != null ? Id.create(relation.get("ref"), TransitLine.class) : Id.create(relation.getUniqueId(), TransitLine.class);
	}
}
