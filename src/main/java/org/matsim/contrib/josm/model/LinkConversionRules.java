package org.matsim.contrib.josm.model;


import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.josm.gui.Preferences;
import org.openstreetmap.josm.data.osm.Way;

public class LinkConversionRules {

    public static final String ID = "matsim:id";
    public static final String FREESPEED = "matsim:freespeed";
    public static final String PERMLANES = "matsim:permlanes";
    public static final String CAPACITY = "matsim:capacity";
    public static final String MODES = "matsim:modes";
    public static final String LENGTH = "matsim:length";
    public static final String TYPE = "matsim:type";
    public static final String HBEFA = "matsim:hbefa";

    static String getId(Way way, long increment, boolean backward) {
        return String.valueOf(way.getUniqueId()) + "_" + increment + (backward ? "_r" : "");
    }

    static String getOrigId(Way way, String id, boolean backward) {
        String origId;
        if (way.hasKey(ID)) {
            origId = way.get(ID) + (backward ? "_r" : "");
        } else {
            origId = id;
        }
        return origId;
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
            if (defaults.hierarchy > Preferences.getMatsimFilterHierarchy()) {
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
            if (defaults.hierarchy > Preferences.getMatsimFilterHierarchy()) {
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
                if (way.getKeys().containsKey(NetworkModel.TAG_RAILWAY)) {
                    modes.add(TransportMode.pt);
                }
                if (way.getKeys().containsKey(NetworkModel.TAG_HIGHWAY)) {
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


    static String getType(Way way, OsmConvertDefaults.OsmWayDefaults defaults) {
        String type = null;
        if (way.getKeys().containsKey(TYPE)) {
            type = way.getKeys().get(TYPE);
        }
        if (type == null) {
            if (way.getKeys().containsKey("highway")) {
                type = way.getKeys().get("highway");
                Double freespeed = getFreespeed(way, defaults);
                if (type != null && freespeed != null) {
                    type = String.format("%s_%d", type, Math.round(freespeed * 3.6));
                }
            }

        }
        return type;
    }

    static boolean isMatsimWay(Way way) {
        final OsmConvertDefaults.OsmWayDefaults defaults = getWayDefaults(way);

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

    static OsmConvertDefaults.OsmWayDefaults getWayDefaults(Way way) {
        String wayType = null;
        if (way.getKeys().containsKey(NetworkModel.TAG_HIGHWAY)) {
            wayType = way.getKeys().get(NetworkModel.TAG_HIGHWAY);
        } else if (way.getKeys().containsKey(NetworkModel.TAG_RAILWAY)) {
            wayType = way.getKeys().get(NetworkModel.TAG_RAILWAY);
        }
        return wayType != null ? OsmConvertDefaults.getWayDefaults().get(wayType) : null;
    }

    /**
    based on the scheme from The TUM Accessibility Atlas: Visualizing Spatial and
    Socioeconomic Disparities in Accessibility to Support Regional Land-Use and
    Transport Planning
    (https://link.springer.com/content/pdf/10.1007%2Fs11067-017-9378-6.pdf)
    */
    static String getHbefaType(Way way, OsmConvertDefaults.OsmWayDefaults defaults) {

        String hbefaType = null;
        if (way.getKeys().containsKey(HBEFA)) {
            hbefaType = way.getKeys().get(HBEFA);
        }

        if (hbefaType == null) {
            Double freespeed = getFreespeed(way, defaults);
            String highway = way.getKeys().get("highway");
            if (way.getKeys().containsKey("highway") && freespeed != null) {

                if (highway.equals("motorway") || highway.equals("motorway_link")) {
                    hbefaType = String.format("%s/%d", "Urban/Motor", Math.round(freespeed * 3.6));
                } else if (highway.equals("primary") || highway.equals("primary_link") || highway.equals("trunk") || highway.equals("trunk_link")) {
                    hbefaType = String.format("%s/%d", "Urban/Trunk", Math.round(freespeed * 3.6));
                } else if (highway.equals("secondary") || highway.equals("secondary_link")) {
                    hbefaType = String.format("%s/%d", "Urban/Distributor", Math.round(freespeed * 3.6));
                } else if (highway.equals("tertiary") || highway.equals("tertiary_link")) {
                    hbefaType = String.format("%s/%d", "Urban/Local", Math.round(freespeed * 3.6));
                } else {
                    hbefaType = String.format("%s/%d", "Urban/Access-residential", Math.round(freespeed * 3.6));
                }
            }
        }
        return hbefaType;
    }
}
