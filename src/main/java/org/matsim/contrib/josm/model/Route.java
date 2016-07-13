package org.matsim.contrib.josm.model;

import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.matsim.pt.transitSchedule.api.Departure;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class Route {
	private final ObjectProperty<Relation> relation = new SimpleObjectProperty<>();
	private final Map<Relation, StopArea> allStopAreas;
	private boolean deleted;
	private ListProperty<MLink> route = new SimpleListProperty<>(FXCollections.observableArrayList());
	private ReadOnlyListWrapper<RouteStop> stops = new ReadOnlyListWrapper<>(FXCollections.observableArrayList());
	private ListProperty<Departure> departures = new SimpleListProperty<>(FXCollections.observableArrayList());
	private StringProperty id = new SimpleStringProperty();
	private StringProperty transportMode = new SimpleStringProperty();

	public Route(Relation _relation, Map<Relation, StopArea> stopAreas) {
		this.relation.setValue(_relation);
		this.allStopAreas = stopAreas;
		id.bind(Bindings.createStringBinding(this::computeMatsimId, relation));
		transportMode.bind(Bindings.createStringBinding(() -> relation.get().get("route"), relation));
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public void setRoute(List<MLink> route) {
		this.route.setAll(route);
	}

	public ObservableList<RouteStop> getStops() {
		stops.clear();
		for (Relation stopAreaRelation : getStopAreaRelations()) {
			// can be from platforms and stops. we can handle both,
			// but of course we only create one facility, even if both are present.
			// the association between the two is handled by a stop area relation,
			// not by the two of them both being in this route.
			StopArea facility = allStopAreas.get(stopAreaRelation);
			if (facility != null && (stops.isEmpty() || facility != stops.get(stops.size() - 1).getStopArea())) {
				stops.add(new RouteStop(facility, 0, 0));
			}
		}
		return stops;
	}

	public StringProperty idProperty() {
		return id;
	}

	public void addDeparture(Departure departure) {
		departures.add(departure);
	}

	public Relation getRelation() {
		return relation.get();
	}

	public ObservableList<MLink> getRoute() {
		return route;
	}

	public Collection<Departure> getDepartures() {
		return departures;
	}

	public List<Relation> getStopAreaRelations() {
		List<Relation> result = new ArrayList<>();
		for (OsmPrimitive osmPrimitive : getStopsAndPlatforms()) {
			for (OsmPrimitive referrer : osmPrimitive.getReferrers()) {
				if (referrer instanceof Relation) {
					result.add((Relation) referrer);
				}
			}
		}
		return result;
	}

	public List<OsmPrimitive> getStopsAndPlatforms() {
		List<OsmPrimitive> result = new ArrayList<>();
		for (RelationMember member : relation.getValue().getMembers()) {
			if (member.hasRole("stop", "platform")) {
				result.add(member.getMember());
			}
		}
		return result;
	}

	public String getId() {
		return id.get();
	}

	private String computeMatsimId() {
		if (relation.get().get("matsim:id") != null) {
			return relation.get().get("matsim:id");
		} else if (relation.get().get("name") != null) {
			return relation.get().get("name");
		} else if (relation.get().get("ref") != null) {
			return relation.get().get("ref");
		} else {
			return Long.toString(relation.get().getUniqueId());
		}
	}

	public String getTransportMode() {
		return transportMode.get();
	}

	public StringProperty transportModeProperty() {
		return transportMode;
	}

}
