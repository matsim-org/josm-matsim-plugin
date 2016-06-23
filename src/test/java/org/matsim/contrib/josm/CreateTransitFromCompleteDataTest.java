package org.matsim.contrib.josm;

import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.josm.model.Export;
import org.matsim.contrib.josm.model.LayerConverter;
import org.matsim.contrib.josm.model.MATSimLayer;
import org.matsim.contrib.josm.model.NetworkModel;
import org.matsim.contrib.josm.scenario.EditableScenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkWriter;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.osm.DataSet;
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
		Main.pref.put("matsim_supportTransit", true);
		InputStream stream = getClass().getResourceAsStream("/test-input/OSMData/busRoute-with-complete-tags.osm.xml");
		DataSet set = OsmReader.parseDataSet(stream, null);

		OsmDataLayer layer = new OsmDataLayer(set, "tmp", null);

		Config config = ConfigUtils.createConfig();
		config.transit().setUseTransit(true);
		NetworkModel listener = NetworkModel.createNetworkModel(set);
		listener.visitAll();
		Main.getLayerManager().addLayer(layer);
		Main.getLayerManager().setActiveLayer(layer);

		Main.getLayerManager().addLayer(LayerConverter.convertWithFullTransit(layer));

		EditableScenario layerScenario = ((MATSimLayer) Main.getLayerManager().getActiveLayer()).getNetworkModel().getScenario();
		Scenario targetScenario = Export.convertIdsAndFilterDeleted(layerScenario);
		new NetworkWriter(targetScenario.getNetwork()).write(new File("network-2.xml").getPath());
		new TransitScheduleWriter(targetScenario.getTransitSchedule()).writeFile(new File("transitSchedule-2.xml").getPath());
	}
}
