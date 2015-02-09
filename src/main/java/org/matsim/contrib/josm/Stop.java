package org.matsim.contrib.josm;

import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;

class Stop {

    TransitStopFacility facility;
    Node position;
    Node platform;
    Way way;

    Stop(TransitStopFacility facility, Node position, Node platform, Way way) {
        this.facility = facility;
        this.position = position;
        this.platform = platform;
        this.way = way;
    }
}
