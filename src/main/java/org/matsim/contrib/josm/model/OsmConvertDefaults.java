package org.matsim.contrib.josm.model;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openstreetmap.josm.data.preferences.StringProperty;
import org.openstreetmap.josm.spi.preferences.IPreferences;

/**
 * Holds the default converting values
 *
 *
 */
public class OsmConvertDefaults {
	private static final Map<String, OsmWayDefaults> wayDefaults = new HashMap<>();

	public static final List<String> highwayTypes = Arrays.asList("motorway", "motorway_link", "trunk", "trunk_link", "primary", "primary_link",
			"secondary", "secondary_link", "tertiary", "tertiary_link", "minor", "unclassified", "residential", "service", "living_street");

	private static final Map<String, StringProperty> properties = new HashMap<>();

	// This is an exhaustive list.
	// See http://wiki.openstreetmap.org/wiki/Proposed_features/Public_Transport
	public static final List<String> routeTypes = Arrays.asList("train", "subway", "monorail", "tram", "bus", "trolleybus", "aerialway", "ferry");

	public static final String[] wayAttributes = { "hierarchy", "lanes", "freespeed", "freespeedFactor", "laneCapacity", "oneway" };

	static {
		load();
	}

	public static void listen(IPreferences pref) {
		pref.addPreferenceChangeListener(preferenceChangeEvent -> load());
	}

	public static Map<String, OsmWayDefaults> getWayDefaults() {
		return wayDefaults;
	}

	private static void load() {

		properties.put("motorway", new StringProperty("matsim_convertDefaults_motorway", "1;2;" + Double.toString(120. / 3.6) + ";1.0;2000;true"));
		properties.put("motorway_link", new StringProperty("matsim_convertDefaults_motorway_link", "2;1;" + Double.toString(80. / 3.6) + ";1.0;1500;true"));
		properties.put("trunk", new StringProperty("matsim_convertDefaults_trunk", "2;1;" + Double.toString(80. / 3.6) + ";1.0;2000;false"));
		properties.put("trunk_link", new StringProperty("matsim_convertDefaults_trunk_link", "2;1;" + Double.toString(50. / 3.6) + ";1.0;1500;false"));
		properties.put("primary", new StringProperty("matsim_convertDefaults_primary", "3;1;" + Double.toString(80. / 3.6) + ";1.0;1500;false"));
		properties.put("primary_link", new StringProperty("matsim_convertDefaults_primary_link", "3;1;" + Double.toString(60. / 3.6) + ";1.0;1500;false"));
		properties.put("secondary", new StringProperty("matsim_convertDefaults_secondary", "4;1;" + Double.toString(60. / 3.6) + ";1.0;1000;false"));
		properties.put("secondary_link", new StringProperty("matsim_convertDefaults_secondary_link", "4;1;" + Double.toString(60. / 3.6) + ";1.0;1000;false"));
		properties.put("tertiary", new StringProperty("matsim_convertDefaults_tertiary", "5;1;" + Double.toString(45. / 3.6) + ";1.0;600;false"));
		properties.put("tertiary_link", new StringProperty("matsim_convertDefaults_tertiary", "5;1;" + Double.toString(45. / 3.6) + ";1.0;600;false"));
		properties.put("minor", new StringProperty("matsim_convertDefaults_minor", "6;1;" + Double.toString(45. / 3.6) + ";1.0;600;false"));
		properties.put("unclassified", new StringProperty("matsim_convertDefaults_unclassified", "6;1;" + Double.toString(45. / 3.6) + ";1.0;600;false"));
		properties.put("residential", new StringProperty("matsim_convertDefaults_residential", "6;1;" + Double.toString(30. / 3.6) + ";1.0;600;false"));
		properties.put("service", new StringProperty("matsim_convertDefaults_service", "6;1;" + Double.toString(15. / 3.6) + ";1.0;300;false"));
		properties.put("living_street", new StringProperty("matsim_convertDefaults_living_street", "6;1;" + Double.toString(15. / 3.6) + ";1.0;300;false"));

		properties.put("rail", new StringProperty("matsim_convertDefaults_rail", "1;1;" + Double.toString(100 / 3.6) + ";1.0;" + Double.toString(Double.MAX_VALUE) + ";false"));
		properties.put("light_rail", new StringProperty("matsim_convertDefaults_light_rail", "2;1;" + Double.toString(60 / 3.6) + ";1.0;" + Double.toString(Double.MAX_VALUE) + ";false"));
		properties.put("tram", new StringProperty("matsim_convertDefaults_tram", "4;1;" + Double.toString(50 / 3.6) + ";1.0;" + Double.toString(Double.MAX_VALUE) + ";false"));
		properties.put("subway", new StringProperty("matsim_convertDefaults_subway", "3;1;" + Double.toString(80 / 3.6) + ";1.0;" + Double.toString(Double.MAX_VALUE) + ";false"));

		for (String type : highwayTypes) {
			String temp = properties.get(type).get();
			String tempArray[] = temp.split(";");

			int hierarchy = Integer.parseInt(tempArray[0]);
			double lanes = Double.parseDouble(tempArray[1]);
			double freespeed = Double.parseDouble(tempArray[2]);
			double freespeedFactor = Double.parseDouble(tempArray[3]);
			double laneCapacity = Double.parseDouble(tempArray[4]);
			boolean oneway = (Boolean.parseBoolean(tempArray[5]));

			wayDefaults.put(type, new OsmWayDefaults(hierarchy, lanes, freespeed, freespeedFactor, laneCapacity, oneway));
		}
	}

	public static void reset() {
		for (String type : highwayTypes) {
			properties.get(type).put(properties.get(type).getDefaultValue());
		}
	}

	public static class OsmWayDefaults {

		public final int hierarchy;
		public final double lanesPerDirection;
		public final double freespeed;
		public final double freespeedFactor;
		public final double laneCapacity;
		public final boolean oneway;

		public OsmWayDefaults(final int hierarchy, final double lanesPerDirection, final double freespeed, final double freespeedFactor,
							final double laneCapacity, final boolean oneway) {
			this.hierarchy = hierarchy;
			this.lanesPerDirection = lanesPerDirection;
			this.freespeed = freespeed;
			this.freespeedFactor = freespeedFactor;
			this.laneCapacity = laneCapacity;
			this.oneway = oneway;
		}
	}

}
