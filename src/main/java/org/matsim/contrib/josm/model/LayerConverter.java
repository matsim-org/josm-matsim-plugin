package org.matsim.contrib.josm.model;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.josm.gui.Preferences;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.pt.utils.CreatePseudoNetwork;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

public class LayerConverter {

	public static MATSimLayer convertToPseudoNetwork(OsmDataLayer osmDataLayer) {

		NetworkModel networkModel = NetworkModel.createNetworkModel(osmDataLayer.data);
		networkModel.visitAll();

		Scenario targetScenario = Export.toScenario(networkModel);
		new CreatePseudoNetwork(targetScenario.getTransitSchedule(), targetScenario.getNetwork(), "pt_")
				.createNetwork();

		Importer importer = new Importer(targetScenario);
		return importer.createMatsimLayer();
	}

	public static MATSimLayer convertWithFullTransit(OsmDataLayer osmLayer) {

		// convert layer data
		NetworkModel networkModel = NetworkModel.createNetworkModel((osmLayer).data);
		networkModel.visitAll();

		Scenario exportedScenario = Export.toScenario(networkModel);

		// check if network should be cleaned
		if ((!Preferences.isSupportTransit()) && Preferences.isCleanNetwork()) {
			new NetworkCleaner().run(exportedScenario.getNetwork());
		}
		Importer importer = new Importer(exportedScenario);
		return importer.createMatsimLayer();
	}

}
