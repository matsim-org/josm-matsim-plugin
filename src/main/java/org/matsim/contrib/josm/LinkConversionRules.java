package org.matsim.contrib.josm;


import org.matsim.api.core.v01.TransportMode;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.osm.Way;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

class LinkConversionRules {

	public static final String FREESPEED = "matsim:freespeed";
	public static final String PERMLANES = "matsim:permlanes";
	public static final String CAPACITY = "matsim:capacity";
	public static final String MODES = "matsim:modes";
	public static final String LENGTH = "matsim:length";

	static String getWayType(Way way) {
		String wayType = null;
		if (way.getKeys().containsKey(NetworkListener.TAG_HIGHWAY)) {
			wayType = way.getKeys().get(NetworkListener.TAG_HIGHWAY);
		} else if (way.getKeys().containsKey(NetworkListener.TAG_RAILWAY)) {
			wayType = way.getKeys().get(NetworkListener.TAG_RAILWAY);
		}
		return wayType;
	}

	static boolean isBackward(Way way, OsmConvertDefaults.OsmWayDefaults defaults) {
		boolean backward;
		if (defaults != null) {
			backward = !defaults.oneway;
			if (way.hasTag("oneway", "yes", "true", "1")) {
				backward = false;
			} else if (way.hasTag("oneway", "-1")) {
				backward = true;
			} else if (way.hasTag("oneway", "no")) {
				backward = true;
			}
			if (defaults.hierarchy > Main.pref.getInteger("matsim_filter_hierarchy", 6)) {
				backward = false;
			}
			if (way.hasTag("access", "no")) {
				backward = false;
			}
		} else {
			backward = false;
		}
		return backward;
	}

	static boolean isForward(Way way, OsmConvertDefaults.OsmWayDefaults defaults) {
		boolean forward;
		if (defaults != null) {
			if (defaults.oneway) {
				forward = true;
			} else {
				forward = true;
			}

			if (way.hasTag("oneway", "yes", "true", "1")) {
				forward = true;
			} else if (way.hasTag("oneway", "-1")) {
				forward = false;
			} else if (way.hasTag("oneway", "no")) {
				forward = true;
			}
			if (defaults.hierarchy > Main.pref.getInteger("matsim_filter_hierarchy", 6)) {
				forward = false;
			}
			if (way.hasTag("access", "no")) {
				forward = false;
			}
		} else {
			forward = true;
		}
		return forward;
	}

	static Set<String> getModes(Way way, OsmConvertDefaults.OsmWayDefaults defaults) {
		Set<String> modes = null;
		if (way.getKeys().containsKey(MODES)) {
			modes = new HashSet<>(Arrays.asList(way.getKeys().get(MODES).split(";")));
		}
		if (defaults != null) {
			if (modes == null) {
				modes = new HashSet<>();
				if (way.getKeys().containsKey(NetworkListener.TAG_RAILWAY)) {
					modes.add(TransportMode.pt);
				}
				if (way.getKeys().containsKey(NetworkListener.TAG_HIGHWAY)) {
					modes.add(TransportMode.car);
				}
			}
		}
		return modes;
	}

	static Double getCapacity(Way way, OsmConvertDefaults.OsmWayDefaults defaults, Double nofLanesPerDirection) {
		Double capacity = null;
		if (way.getKeys().containsKey(CAPACITY)) {
			capacity = parseDoubleIfPossible(way.getKeys().get(CAPACITY));
		}
		if (defaults != null) {
			if (capacity == null) {
				capacity = nofLanesPerDirection * defaults.laneCapacity;
			}
		}
		return capacity;
	}

	static Double getLanesPerDirection(Way way, OsmConvertDefaults.OsmWayDefaults defaults, boolean forward, boolean backward) {
		Double nofLanesPerDirection = null;
		if (way.getKeys().containsKey(PERMLANES)) {
			nofLanesPerDirection = parseDoubleIfPossible(way.getKeys().get(PERMLANES));
		}
		if (defaults != null) {
			if (nofLanesPerDirection == null) {
				nofLanesPerDirection = defaults.lanesPerDirection;
				if (way.getKeys().containsKey("lanes")) {
					Double noOfLanes = parseDoubleIfPossible(way.getKeys().get("lanes"));
					if (noOfLanes != null) {
						if (forward && backward) {
							nofLanesPerDirection = noOfLanes / 2.0;
						} else {
							nofLanesPerDirection = noOfLanes;
						}
					}
				}
			}
		}
		return nofLanesPerDirection;
	}

	static Double getFreespeed(Way way, OsmConvertDefaults.OsmWayDefaults defaults) {
		Double freespeed = null;
		if (way.getKeys().containsKey(FREESPEED)) {
			freespeed = parseDoubleIfPossible(way.getKeys().get(FREESPEED));
		}
		if (freespeed == null) {
			if (defaults != null) {
				freespeed = defaults.freespeed;
				if (way.getKeys().containsKey("maxspeed")) {
					Double maxspeedInKmH = parseDoubleIfPossible(way.getKeys().get("maxspeed"));
					if (maxspeedInKmH != null) {
						freespeed = maxspeedInKmH / 3.6; // convert km/h to m/s
					}
				}
			}
		}
		return freespeed;
	}

	static boolean isMatsimWay(Way way) {
		final String wayType = getWayType(way);
		final OsmConvertDefaults.OsmWayDefaults defaults = wayType != null ? OsmConvertDefaults.getWayDefaults().get(wayType) : null;

		final boolean forward = isForward(way, defaults);
		final boolean backward = isBackward(way, defaults);
		final Double freespeed = getFreespeed(way, defaults);
		final Double nofLanesPerDirection = getLanesPerDirection(way, defaults, forward, backward);
		final Double capacity = getCapacity(way, defaults, nofLanesPerDirection);
		final Set<String> modes = getModes(way, defaults);

		return capacity != null && freespeed != null && nofLanesPerDirection != null && modes != null;
	}

	static Double getTaggedLength(Way way) {
		Double taggedLength = null;
		if (way.getKeys().containsKey(LENGTH)) {
			taggedLength = parseDoubleIfPossible(way.getKeys().get(LENGTH));
		}
		return taggedLength;
	}

	static Double parseDoubleIfPossible(String string) {
		try {
			return Double.parseDouble(string);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
