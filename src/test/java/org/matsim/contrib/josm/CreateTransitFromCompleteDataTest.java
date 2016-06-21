package org.matsim.contrib.josm;

import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.josm.scenario.EditableScenarioUtils;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;


public class CreateTransitFromCompleteDataTest {

	@Rule
	public JOSMTestRules test = new JOSMTestRules().preferences().projection();

	@Test
	public void createTransit() throws IllegalDataException, IOException {
		Main.pref.put("matsim_supportTransit", true);
		InputStream stream = getClass().getResourceAsStream("/test-input/OSMData/busRoute-with-complete-tags.osm.xml");
		DataSet set = OsmReader.parseDataSet(stream, null);

		OsmDataLayer layer = new OsmDataLayer(set, "tmp", null);

		Config config = ConfigUtils.createConfig();
		config.transit().setUseTransit(true);
		NetworkListener listener = new NetworkListener(set, EditableScenarioUtils.createScenario(config), new HashMap<Way, List<Link>>(),
				new HashMap<Link, List<WaySegment>>(), new HashMap<Relation, TransitStopFacility>());
		Main.pref.addPreferenceChangeListener(listener);


		listener.visitAll();
		set.addDataSetListener(listener);
		Main.getLayerManager().addLayer(layer);
		Main.getLayerManager().setActiveLayer(layer);

		LayerConverter converter = new LayerConverter(layer);
		converter.run();
		Main.getLayerManager().addLayer(converter.getMatsimLayer());

		new ExportTask(new File("network-2.xml"), (MATSimLayer) Main.getLayerManager().getActiveLayer()).realRun();
		new TransitScheduleExporter(new File("transitSchedule-2.xml")).run((MATSimLayer) Main.getLayerManager().getActiveLayer());
	}
}
