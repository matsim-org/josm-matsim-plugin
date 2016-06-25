package org.matsim.contrib.josm.model;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.josm.gui.Preferences;
import org.matsim.contrib.josm.scenario.EditableScenario;
import org.matsim.contrib.josm.scenario.EditableTransitStopFacility;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.utils.CreatePseudoNetwork;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

import java.util.ArrayList;

public class LayerConverter {

	public static MATSimLayer convertToPseudoNetwork(OsmDataLayer osmDataLayer) {

		NetworkModel networkModel = NetworkModel.createNetworkModel(osmDataLayer.data);
		networkModel.visitAll();
		EditableScenario sourceScenario = networkModel.getScenario();

		emptyNetwork(sourceScenario);
		EditableScenario targetScenario = Export.toScenario(networkModel);

		new CreatePseudoNetwork(targetScenario.getTransitSchedule(), targetScenario.getNetwork(), "pt_")
				.createNetwork();

		Importer importer = new Importer(targetScenario);
		return importer.createMatsimLayer();
	}

	public static MATSimLayer convertWithFullTransit(OsmDataLayer osmLayer) {

		// convert layer data
		NetworkModel networkModel = NetworkModel.createNetworkModel((osmLayer).data);
		networkModel.visitAll();

		EditableScenario exportedScenario = Export.toScenario(networkModel);

		// check if network should be cleaned
		if ((!Preferences.isSupportTransit()) && Preferences.isCleanNetwork()) {
			new NetworkCleaner().run(exportedScenario.getNetwork());
		}
		Importer importer = new Importer(exportedScenario);
		return importer.createMatsimLayer();
	}

	private static void emptyNetwork(EditableScenario sourceScenario) {
		for (Id<Link> linkId : new ArrayList<>(sourceScenario.getNetwork().getLinks().keySet())) {
			sourceScenario.getNetwork().removeLink(linkId);
		}
		for (Id<Node> nodeId : new ArrayList<>(sourceScenario.getNetwork().getNodes().keySet())) {
			sourceScenario.getNetwork().removeNode(nodeId);
		}
		for (EditableTransitStopFacility transitStopFacility : sourceScenario.getTransitSchedule().getEditableFacilities().values()) {
			transitStopFacility.setNodeId(null);
		}
		for (TransitLine transitLine : sourceScenario.getTransitSchedule().getTransitLines().values()) {
			for (TransitRoute transitRoute : transitLine.getRoutes().values()) {
				transitRoute.setRoute(null);
			}
		}
	}

}
