package org.matsim.contrib.josm.model;

import javafx.beans.property.ListProperty;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class Route {
	private final Relation relation;
	private final Map<Relation, StopArea> allStopAreas;
	private boolean deleted;
	private List<MLink> route;
	private ReadOnlyListWrapper<RouteStop> stops = new ReadOnlyListWrapper<>(FXCollections.observableArrayList());
	private ListProperty<Departure> departures = new SimpleListProperty<>(FXCollections.observableArrayList());

	public Route(Relation relation, Map<Relation, StopArea> stopAreas) {
		this.relation = relation;
		this.allStopAreas = stopAreas;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public void setRoute(List<MLink> route) {
		this.route = route;
	}

	public List<RouteStop> getStops() {
		stops.clear();
		for (RelationMember member : relation.getMembers()) {
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

	public Object getId() {
		return Id.create(relation.getUniqueId(), TransitRoute.class);
	}

	public Object getRealId() {
		return relation.get("ref") != null ? Id.create(relation.get("ref"), TransitRoute.class) : getId();
	}

	public void addDeparture(Departure departure) {
		departures.add(departure);
	}

	public Relation getRelation() {
		return relation;
	}

	public List<MLink> getRoute() {
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
		return relation.get("ref") != null ? Id.create(relation.get("ref"), TransitRoute.class) : Id.create(relation.getUniqueId(), TransitRoute.class);
	}

	public String getTransportMode() {
		return relation.get("route");
	}

}
