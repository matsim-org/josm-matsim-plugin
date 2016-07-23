package org.matsim.contrib.josm.model;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.openstreetmap.josm.data.osm.WaySegment;

import java.util.List;
import java.util.Set;

public class MLink {
	private boolean reverseWayDirection = false;
	private Double length;
	private Double freespeed;
	private Double capacity;
	private Double numberOfLanes;
	private Set<String> allowedModes;
	private String origId;
	private List<WaySegment> segments;
	private MNode fromNode;
	private MNode toNode;

	public MLink(MNode fromNode, MNode toNode) {

		this.fromNode = fromNode;
		this.toNode = toNode;
	}

	public void setLength(Double length) {
		this.length = length;
	}

	public Double getLength() {
		return length;
	}

	public void setFreespeed(Double freespeed) {
		this.freespeed = freespeed;
	}

	public Double getFreespeed() {
		return freespeed;
	}

	public void setCapacity(Double capacity) {
		this.capacity = capacity;
	}

	public Double getCapacity() {
		return capacity;
	}

	public void setNumberOfLanes(Double numberOfLanes) {
		this.numberOfLanes = numberOfLanes;
	}

	public Double getNumberOfLanes() {
		return numberOfLanes;
	}

	public void setAllowedModes(Set<String> allowedModes) {
		this.allowedModes = allowedModes;
	}

	public Set<String> getAllowedModes() {
		return allowedModes;
	}

	public void setOrigId(String origId) {
		this.origId = origId;
	}

	public String getOrigId() {
		return origId;
	}

	public void setSegments(List<WaySegment> segments) {
		this.segments = segments;
	}

	public List<WaySegment> getSegments() {
		return segments;
	}

	public Id<Link> getId() {
		return Id.createLinkId(getOrigId());
	}

	public MNode getFromNode() {
		return fromNode;
	}

	public MNode getToNode() {
		return toNode;
	}

	public boolean isReverseWayDirection() {
		return reverseWayDirection;
	}

	public void setReverseWayDirection(boolean reverseWayDirection) {
		this.reverseWayDirection = reverseWayDirection;
	}
}
