package org.matsim.contrib.josm;

import org.junit.Rule;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.josm.actions.TransitScheduleTest;
import org.matsim.contrib.josm.model.Export;
import org.matsim.contrib.josm.model.LayerConverter;
import org.matsim.contrib.josm.model.MATSimLayer;
import org.matsim.contrib.josm.model.NetworkModel;
import org.matsim.contrib.osm.CreateStopAreas;
import org.matsim.contrib.osm.IncompleteRoutesTest;
import org.matsim.contrib.osm.MasterRoutesTest;
import org.matsim.contrib.osm.UpdateStopTags;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.progress.PleaseWaitProgressMonitor;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;


public class CreateTransitFromIncompleteDataTest {

	@Rule
	public JOSMTestRules test = new JOSMTestRules().preferences().projection();

	@org.junit.Test
	public void createTransit() throws IllegalDataException, IOException {
		Main.pref.put("matsim_supportTransit", true);

		System.out.println("Fixture initialized");

		System.out.println("Reading DataSet");
		InputStream stream = getClass().getResourceAsStream("/test-input/OSMData/busRoute-without-stop-areas.osm.xml");
		DataSet set = OsmReader.parseDataSet(stream, null);
		System.out.println("DataSet ready");

		OsmDataLayer layer = new OsmDataLayer(set, "tmp", null);

		Config config = ConfigUtils.createConfig();
		config.transit().setUseTransit(true);
		NetworkModel listener = NetworkModel.createNetworkModel(set);
		listener.visitAll();
		Main.getLayerManager().addLayer(layer);
		Main.getLayerManager().setActiveLayer(layer);
		System.out.println("Layer added");

		System.out.println("Starting Validations");
		List<Test> tests = Arrays.asList(new IncompleteRoutesTest(), new UpdateStopTags(), new MasterRoutesTest(), new CreateStopAreas());
		for (Test test : tests) {
			System.out.println("Starting " + test.getName());

			test.startTest(null);
			test.visit(layer.data.allPrimitives());
			test.endTest();
			System.out.println(test.getErrors().size() + " errors found");
			int i = 0;
			for (TestError error : test.getErrors()) {
				if (error.isFixable()) {
					final Command fixCommand = error.getFix();
					if (fixCommand != null) {
						if (i % 50 == 0) {
							System.out.println("Fixing error #" + i);
						}
						fixCommand.executeCommand();
						i++;
					}
				}
			}
		}

		System.out.println("Converting data");
		Main.getLayerManager().addLayer(LayerConverter.convertWithFullTransit(layer));
		System.out.println("Exporting data");

		TransitScheduleTest test = new TransitScheduleTest();
		PleaseWaitProgressMonitor progMonitor = new
				PleaseWaitProgressMonitor("Validation");
		// convertWithFullTransit validator tests
		test.startTest(progMonitor);
		test.visit(Main.getLayerManager().getEditDataSet().allPrimitives());
		test.endTest();
		progMonitor.finishTask();
		progMonitor.close();

		for (TestError error : test.getErrors()) {
			if (error.isFixable()) {
				final Command fixCommand = error.getFix();
				if (fixCommand != null) {
					fixCommand.executeCommand();
				}
			}
		}

		Scenario targetScenario = Export.toScenario(((MATSimLayer) Main.getLayerManager().getActiveLayer()).getNetworkModel());
		new NetworkWriter(targetScenario.getNetwork()).write(new File("network.xml").getPath());
		new TransitScheduleWriter(targetScenario.getTransitSchedule()).writeFile(new File("transitSchedule.xml").getPath());
	}
}
