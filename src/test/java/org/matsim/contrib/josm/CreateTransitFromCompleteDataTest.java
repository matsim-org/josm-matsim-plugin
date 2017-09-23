package org.matsim.contrib.josm;

import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.josm.gui.Preferences;
import org.matsim.contrib.josm.model.Export;
import org.matsim.contrib.josm.model.LayerConverter;
import org.matsim.contrib.josm.model.MATSimLayer;
import org.matsim.contrib.josm.model.NetworkModel;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;


public class CreateTransitFromCompleteDataTest {

	@Rule
	public JOSMTestRules test = new JOSMTestRules().preferences().projection();

	@Test
	public void createTransit() throws IllegalDataException, IOException {
		Preferences.setSupportTransit(true);
		InputStream stream = getClass().getResourceAsStream("/test-input/OSMData/busRoute-with-complete-tags.osm.xml");
		DataSet set = OsmReader.parseDataSet(stream, null);

		OsmDataLayer layer = new OsmDataLayer(set, "tmp", null);

		Config config = ConfigUtils.createConfig();
		config.transit().setUseTransit(true);
		NetworkModel listener = NetworkModel.createNetworkModel(set);
		listener.visitAll();
		MainApplication.getLayerManager().addLayer(layer);
		MainApplication.getLayerManager().setActiveLayer(layer);

		MainApplication.getLayerManager().addLayer(LayerConverter.convertWithFullTransit(layer));

		Scenario targetScenario = Export.toScenario(((MATSimLayer) MainApplication.getLayerManager().getActiveLayer()).getNetworkModel());
		new NetworkWriter(targetScenario.getNetwork()).write(new File("network-2.xml").getPath());
		new TransitScheduleWriter(targetScenario.getTransitSchedule()).writeFile(new File("transitSchedule-2.xml").getPath());
	}
}
