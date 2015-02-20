package org.matsim.contrib.josm;

import java.util.HashMap;
import java.util.List;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

public class LayerConverter {
	
	private OsmDataLayer osmLayer;
	private MATSimLayer matsimLayer;
	
	public LayerConverter (OsmDataLayer osmLayer) {
		this.osmLayer = osmLayer;
	}
	
	public MATSimLayer getMatsimLayer() {
		return matsimLayer;
	}
	
	
	
	public void run() {

		// scenario for converted data
		Scenario sourceScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		sourceScenario.getConfig().scenario().setUseTransit(Preferences.isSupportTransit());
		sourceScenario.getConfig().scenario().setUseVehicles(Preferences.isSupportTransit());

		// convert layer data
        NetworkListener networkListener = new NetworkListener((osmLayer).data, sourceScenario, new HashMap<Way, List<Link>>(), new HashMap<Link, List<WaySegment>>(), new HashMap<Relation, TransitRoute>());
        networkListener.visitAll();

        // check if network should be cleaned
		if ((!Preferences.isSupportTransit()) && Preferences.isCleanNetwork()) {
			new NetworkCleaner().run(sourceScenario.getNetwork());
		}
        Importer importer = new Importer(sourceScenario, Main.getProjection());
        importer.run();
        matsimLayer = importer.getLayer();
	}

}
