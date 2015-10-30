package org.matsim.contrib.josm.scenario;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.Map;

public class EditableTransitStopFacility implements TransitStopFacility {

    Id<TransitStopFacility> id;
    boolean isBlockingLane;
    Id<Node> nodeId;
    Id<Link> linkId;
    String name;
    String stopPostAreaId;
    Coord coord;
    Map<String, Object> customAttributes;

    public EditableTransitStopFacility(Id<TransitStopFacility> id) {
        this.id = id;
    }

    @Override
    public Id<TransitStopFacility> getId() {
        return id;
    }

    @Override
    public Map<String, Object> getCustomAttributes() {
        return customAttributes;
    }

    @Override
    public Coord getCoord() {
        return coord;
    }

    public void setCoord(Coord coord) {
        this.coord = coord;
    }

    public boolean getIsBlockingLane() {
        return isBlockingLane;
    }


    @Override
    public Id<Link> getLinkId() {
        return linkId;
    }

    @Override
    public void setLinkId(Id<Link> linkId) {
        this.linkId = linkId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getStopPostAreaId() {
        return stopPostAreaId;
    }

    @Override
    public void setStopPostAreaId(String stopPostAreaId) {
        this.stopPostAreaId = stopPostAreaId;
    }

    public void setIsBlockingLane(boolean isBlockingLane) {
        this.isBlockingLane = isBlockingLane;
    }

    public Id<Node> getNodeId() {
        return nodeId;
    }

    public void setNodeId(Id<Node> nodeId) {
        this.nodeId = nodeId;
    }

}
