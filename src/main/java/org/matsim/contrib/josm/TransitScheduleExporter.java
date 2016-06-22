package org.matsim.contrib.josm;

import org.matsim.contrib.josm.model.Export;
import org.matsim.contrib.josm.scenario.EditableScenario;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;

import java.io.File;

public class TransitScheduleExporter {

	private File scheduleFile;

	public TransitScheduleExporter(File scheduleFile) {
		this.scheduleFile = scheduleFile;
	}

	public void run(MATSimLayer layer) {
		EditableScenario targetScenario = Export.convertIdsAndFilterDeleted(layer.getScenario());
		new TransitScheduleWriter(targetScenario.getTransitSchedule()).writeFile(scheduleFile.getPath());
	}

}
