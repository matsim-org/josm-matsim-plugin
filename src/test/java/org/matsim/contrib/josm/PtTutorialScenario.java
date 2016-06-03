package org.matsim.contrib.josm;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkReaderMatsimV1;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.openstreetmap.josm.Main;

import java.io.File;
import java.net.URL;

public class PtTutorialScenario {

	static final URL networkUrl = PtTutorialScenario.class.getResource("/test-input/pt-tutorial/multimodalnetwork.xml");
	static final URL transitScheduleUrl = PtTutorialScenario.class.getResource("/test-input/pt-tutorial/transitschedule.xml");

	public static Scenario scenario() {
		Config config = ConfigUtils.createConfig();
		config.transit().setUseTransit(true);
		Scenario scenario = ScenarioUtils.createScenario(config);
		new NetworkReaderMatsimV1(scenario.getNetwork()).parse(networkUrl);
		new TransitScheduleReader(scenario).readFile(transitScheduleUrl.getFile());
		return scenario;
	}

	public static MATSimLayer layer() {
		Importer importer = new Importer(new File(networkUrl.getFile()), new File(transitScheduleUrl.getFile()));
		importer.run();
		return importer.getLayer();
	}

}
