package org.matsim.contrib.josm;

import org.openstreetmap.josm.Main;

import java.util.HashMap;
import java.util.Map;

/**
 * Holds the default converting values
 * 
 * 
 */
class OsmConvertDefaults {
	private static final Map<String, OsmWayDefaults> wayDefaults = new HashMap<>();

	static final String[] wayTypes = { "motorway", "motorway_link", "trunk",
			"trunk_link", "primary", "primary_link", "secondary", "tertiary",
			"minor", "unclassified", "residential", "living_street", "rail",
			"light_rail", "tram", "subway" };

	static final String[] wayAttributes = { "hierarchy", "lanes", "freespeed",
			"freespeedFactor", "laneCapacity", "oneway" };

	public static Map<String, OsmWayDefaults> getWayDefaults() {
		return wayDefaults;
	}

	static void load() {

		Map<String, String> values = new HashMap<>();
		values.put(
				"motorway",
				Main.pref.get("matsim_convertDefaults_motorway", "1;2;"
						+ Double.toString(120. / 3.6) + ";1.0;2000;true"));
		values.put(
				"motorway_link",
				Main.pref.get("matsim_convertDefaults_motorway_link", "2;1;"
						+ Double.toString(80. / 3.6) + ";1.0;1500;true"));
		values.put("trunk", Main.pref.get("matsim_convertDefaults_trunk",
				"2;1;" + Double.toString(80. / 3.6) + ";1.0;2000;false"));
		values.put(
				"trunk_link",
				Main.pref.get("matsim_convertDefaults_trunk_link", "2;1;"
						+ Double.toString(50. / 3.6) + ";1.0;1500;false"));
		values.put("primary", Main.pref.get("matsim_convertDefaults_primary",
				"3;1;" + Double.toString(80. / 3.6) + ";1.0;1500;false"));
		values.put(
				"primary_link",
				Main.pref.get("matsim_convertDefaults_primary_link", "3;1;"
						+ Double.toString(600. / 3.6) + ";1.0;1500;false"));
		values.put(
				"secondary",
				Main.pref.get("matsim_convertDefaults_secondary", "4;1;"
						+ Double.toString(60. / 3.6) + ";1.0;1000;false"));
		values.put(
				"tertiary",
				Main.pref.get("matsim_convertDefaults_tertiary", "5;1;"
						+ Double.toString(45. / 3.6) + ";1.0;600;false"));
		values.put(
				"minor",
				Main.pref.get("matsim_convertDefaults_minor",
						"6;1;" + Double.toString(45. / 3.6) + ";1.0;600;false"));
		values.put(
				"unclassified",
				Main.pref.get("matsim_convertDefaults_unclassified", "6;1;"
						+ Double.toString(45. / 3.6) + ";1.0;600;false"));
		values.put(
				"residential",
				Main.pref.get("matsim_convertDefaults_residential", "6;1;"
						+ Double.toString(30. / 3.6) + ";1.0;600;false"));
		values.put(
				"living_street",
				Main.pref.get("matsim_convertDefaults_living_street", "6;1;"
						+ Double.toString(15. / 3.6) + ";1.0;300;false"));

		values.put(
				"rail",
				Main.pref.get(
						"matsim_convertDefaults_rail",
						"1;1;" + Double.toString(100 / 3.6) + ";1.0;"
								+ Double.toString(Double.MAX_VALUE) + ";false"));
		values.put(
				"light_rail",
				Main.pref.get(
						"matsim_convertDefaults_light_rail",
						"2;1;" + Double.toString(60 / 3.6) + ";1.0;"
								+ Double.toString(Double.MAX_VALUE) + ";false"));
		values.put(
				"tram",
				Main.pref.get(
						"matsim_convertDefaults_tram",
						"4;1;" + Double.toString(50 / 3.6) + ";1.0;"
								+ Double.toString(Double.MAX_VALUE) + ";false"));
		values.put(
				"subway",
				Main.pref.get(
						"matsim_convertDefaults_subway",
						"3;1;" + Double.toString(80 / 3.6) + ";1.0;"
								+ Double.toString(Double.MAX_VALUE) + ";false"));

		for (String type : wayTypes) {
			String temp = values.get(type);
			String tempArray[] = temp.split(";");

			int hierarchy = Integer.parseInt(tempArray[0]);
			double lanes = Double.parseDouble(tempArray[1]);
			double freespeed = Double.parseDouble(tempArray[2]);
			double freespeedFactor = Double.parseDouble(tempArray[3]);
			double laneCapacity = Double.parseDouble(tempArray[4]);
			boolean oneway = (Boolean.parseBoolean(tempArray[5]));

			wayDefaults.put(type, new OsmWayDefaults(hierarchy, lanes,
					freespeed, freespeedFactor, laneCapacity, oneway));
		}
	}

	static void reset() {

		Main.pref.put("matsim_convertDefaults_motorway",
				"1;2;" + Double.toString(120. / 3.6) + ";1.0;2000;true");
		Main.pref.put("matsim_convertDefaults_motorway_link",
				"2;1;" + Double.toString(80. / 3.6) + ";1.0;1500;true");
		Main.pref.put("matsim_convertDefaults_trunk",
				"2;1;" + Double.toString(80. / 3.6) + ";1.0;2000;false");
		Main.pref.put("matsim_convertDefaults_trunk_link",
				"2;1;" + Double.toString(50. / 3.6) + ";1.0;1500;false");
		Main.pref.put("matsim_convertDefaults_primary",
				"3;1;" + Double.toString(80. / 3.6) + ";1.0;1500;false");
		Main.pref.put("matsim_convertDefaults_primary_link",
				"3;1;" + Double.toString(600. / 3.6) + ";1.0;1500;false");
		Main.pref.put("matsim_convertDefaults_secondary",
				"4;1;" + Double.toString(60. / 3.6) + ";1.0;1000;false");
		Main.pref.put("matsim_convertDefaults_tertiary",
				"5;1;" + Double.toString(45. / 3.6) + ";1.0;600;false");
		Main.pref.put("matsim_convertDefaults_minor",
				"6;1;" + Double.toString(45. / 3.6) + ";1.0;600;false");
		Main.pref.put("matsim_convertDefaults_unclassified",
				"6;1;" + Double.toString(45. / 3.6) + ";1.0;600;false");
		Main.pref.put("matsim_convertDefaults_residential",
				"6;1;" + Double.toString(30. / 3.6) + ";1.0;600;false");
		Main.pref.put("matsim_convertDefaults_living_street",
				"6;1;" + Double.toString(15. / 3.6) + ";1.0;300;false");
	}

	static class OsmWayDefaults {

		public final int hierarchy;
		public final double lanes;
		public final double freespeed;
		public final double freespeedFactor;
		public final double laneCapacity;
		public final boolean oneway;

		public OsmWayDefaults(final int hierarchy, final double lanes,
				final double freespeed, final double freespeedFactor,
				final double laneCapacity, final boolean oneway) {
			this.hierarchy = hierarchy;
			this.lanes = lanes;
			this.freespeed = freespeed;
			this.freespeedFactor = freespeedFactor;
			this.laneCapacity = laneCapacity;
			this.oneway = oneway;
		}
	}

}
