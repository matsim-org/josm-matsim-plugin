package org.matsim.contrib.josm.model;

import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.matsim.contrib.josm.gui.Preferences;
import org.matsim.pt.transitSchedule.api.Departure;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.dialogs.relation.sort.WayConnectionType;
import org.openstreetmap.josm.gui.dialogs.relation.sort.WayConnectionTypeCalculator;

import java.util.*;

public class Route {
	private final ObjectProperty<Relation> relation = new SimpleObjectProperty<>();
	private final Map<Relation, StopArea> allStopAreas;
	private final Map<Way, List<MLink>> allLinks;
	private boolean deleted;
	private ListProperty<MLink> route = new SimpleListProperty<>(FXCollections.observableArrayList());
	private ReadOnlyListWrapper<RouteStop> stops = new ReadOnlyListWrapper<>(FXCollections.observableArrayList());
	private ListProperty<Departure> departures = new SimpleListProperty<>(FXCollections.observableArrayList());
	private StringProperty id = new SimpleStringProperty();
	private StringProperty transportMode = new SimpleStringProperty();

	public Route(Relation _relation, Map<Relation, StopArea> stopAreas, Map<Way, List<MLink>> way2Links) {
		this.relation.setValue(_relation);
		this.allStopAreas = stopAreas;
		this.allLinks = way2Links;
		id.bind(Bindings.createStringBinding(this::computeMatsimId, relation));
		transportMode.bind(Bindings.createStringBinding(() -> relation.get().get("route"), relation));
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	public boolean isDeleted() {
		return deleted;
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
		if (isExplicitelyMatsimTagged(relation.get()) || !Preferences.isTransitLite()) {
			List<MLink> networkRoute = determineNetworkRoute(relation.get());
			this.route.setAll(networkRoute);
		}
		return route;
	}

	private boolean isExplicitelyMatsimTagged(Relation relation) {
		return relation.get("matsim:id") != null;
	}

	private List<MLink> determineNetworkRoute(Relation relation) {
		List<MLink> links = new ArrayList<>();
		List<RelationMember> members = relation.getMembers();
		if (!members.isEmpty()) { // WayConnectionTypeCalculator
			// will crash otherwise
			WayConnectionTypeCalculator calc = new WayConnectionTypeCalculator();
			List<WayConnectionType> connections = calc.updateLinks(members);
			for (int i=0; i<members.size(); i++) {
				RelationMember member = members.get(i);
				if (member.isWay()) {
					Way way = member.getWay();
					List<MLink> wayLinks = allLinks.get(way);
					if (wayLinks != null) {
						wayLinks = new ArrayList<>(wayLinks);
						if (connections.get(i).direction.equals(WayConnectionType.Direction.FORWARD)) {
							for (MLink link : wayLinks) {
								if (!link.isReverseWayDirection()) {
									links.add(link);
								}
							}
						} else if (connections.get(i).direction.equals(WayConnectionType.Direction.BACKWARD)) {
							// reverse order of links if backwards
							Collections.reverse(wayLinks);
							for (MLink link : wayLinks) {
								if (link.isReverseWayDirection()) {
									links.add(link);
								}
							}
						}
					}
				}
			}
		}
		return links;
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
