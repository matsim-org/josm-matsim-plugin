package org.matsim.contrib.josm.model;

import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;

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
	private StringProperty realId = new SimpleStringProperty();
	private StringProperty transportMode = new SimpleStringProperty();

	public Route(Relation _relation, Map<Relation, StopArea> stopAreas) {
		this.relation.setValue(_relation);
		this.allStopAreas = stopAreas;
		realId.bind(Bindings.createStringBinding(() -> relation.get().get("ref") != null ? relation.get().get("ref") : getId() , relation));
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
		for (RelationMember member : relation.getValue().getMembers()) {
			// can be platforms and stops. we can handle both,
			// but of course we only create one facility, even if both are present.
			// the association between the two is handled by a stop area relation,
			// not by the two of them both being in this route.
			StopArea facility = findStopArea(member);
			if (facility != null && (stops.isEmpty() || facility != stops.get(stops.size()-1).getStopArea())) {
				stops.add(new RouteStop(facility, 0, 0));
			}
		}

		return stops;
	}

	public String getId() {
		return Long.toString(relation.getValue().getUniqueId());
	}

	public String getRealId() {
		return realId.get();
	}

	public StringProperty realIdProperty() {
		return realId;
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

	private StopArea findStopArea(RelationMember member) {
		if (member.hasRole("stop", "platform")) {
			for (OsmPrimitive referrer : member.getMember().getReferrers()) {
				if (referrer instanceof Relation) {
					StopArea facility = allStopAreas.get(referrer);
					if (facility != null) {
						return facility;
					}
				}
			}
		}
		return null; // not a transit stop facility
	}

	public Id<TransitRoute> getMatsimId() {
		return relation.get().get("ref") != null ? Id.create(relation.get().get("ref"), TransitRoute.class) : Id.create(relation.get().getUniqueId(), TransitRoute.class);
	}

	public String getTransportMode() {
		return transportMode.get();
	}

	public StringProperty transportModeProperty() {
		return transportMode;
	}

}
