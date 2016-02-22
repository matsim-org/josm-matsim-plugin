package org.matsim.contrib.josm;

import org.matsim.api.core.v01.Scenario;
import org.matsim.pt.transitSchedule.api.*;

import java.io.File;

class TransitScheduleExporter {

	private File scheduleFile;

	public TransitScheduleExporter(File scheduleFile) {
		this.scheduleFile = scheduleFile;
	}

	void run(MATSimLayer layer) {
		Scenario targetScenario = ExportTask.convertIdsAndFilterDeleted(layer.getScenario());

		// if (Main.pref.getBoolean("matsim_transit_lite")) {
		// CreatePseudoNetwork pseudoNetworkCreator = new
		// CreatePseudoNetwork(targetScenario.getTransitSchedule(),
		// targetScenario.getNetwork(), "dummy_");
		// pseudoNetworkCreator.createNetwork();
		// }
		if (targetScenario.getTransitSchedule() != null) {
			new TransitScheduleWriter(targetScenario.getTransitSchedule()).writeFile(scheduleFile.getPath());
		}
	}

}
