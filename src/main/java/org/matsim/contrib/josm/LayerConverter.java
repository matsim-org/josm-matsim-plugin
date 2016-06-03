package org.matsim.contrib.josm;

import java.util.HashMap;
import java.util.List;

import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.josm.scenario.EditableScenario;
import org.matsim.contrib.josm.scenario.EditableScenarioUtils;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

public class LayerConverter {

	private OsmDataLayer osmLayer;
	private MATSimLayer matsimLayer;

	public LayerConverter(OsmDataLayer osmLayer) {
		this.osmLayer = osmLayer;
	}

	public MATSimLayer getMatsimLayer() {
		return matsimLayer;
	}

	public void run() {

		// scenario for converted data
		Config config = ConfigUtils.createConfig();
		config.transit().setUseTransit(Preferences.isSupportTransit());
		EditableScenario sourceScenario = EditableScenarioUtils.createScenario(config);

		// convert layer data
		NetworkListener networkListener = new NetworkListener((osmLayer).data, sourceScenario, new HashMap<Way, List<Link>>(),
				new HashMap<Link, List<WaySegment>>(), new HashMap<Relation, TransitStopFacility>());
		networkListener.visitAll();

		EditableScenario exportedScenario = ExportTask.convertIdsAndFilterDeleted(sourceScenario);

		// check if network should be cleaned
		if ((!Preferences.isSupportTransit()) && Preferences.isCleanNetwork()) {
			new NetworkCleaner().run(exportedScenario.getNetwork());
		}
		Importer importer = new Importer(exportedScenario);
		importer.run();
		matsimLayer = importer.getLayer();
	}

}
