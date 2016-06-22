package org.matsim.contrib.josm;

import org.matsim.contrib.josm.scenario.EditableScenario;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;

import java.io.File;

class TransitScheduleExporter {

	private File scheduleFile;

	public TransitScheduleExporter(File scheduleFile) {
		this.scheduleFile = scheduleFile;
	}

	void run(MATSimLayer layer) {
		EditableScenario targetScenario = ExportTask.convertIdsAndFilterDeleted(layer.getScenario());
		new TransitScheduleWriter(targetScenario.getTransitSchedule()).writeFile(scheduleFile.getPath());
	}

}
