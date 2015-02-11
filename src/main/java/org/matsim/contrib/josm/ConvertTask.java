package org.matsim.contrib.josm;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.io.OsmTransferException;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

/**
 * The Task that handles the convert action. Creates new OSM primitives with
 * MATSim Tag scheme
 * 
 * @author Nico
 * 
 */

class ConvertTask extends PleaseWaitRunnable {

	private MATSimLayer newLayer;

	/**
	 * Creates a new Convert task
	 * 
	 * @see PleaseWaitRunnable
	 */
	public ConvertTask() {
		super("Converting to MATSim Network");
	}

	/**
	 * @see org.openstreetmap.josm.gui.PleaseWaitRunnable#cancel()
	 */
	@Override
	protected void cancel() {
		// TODO Auto-generated method stub
	}

	/**
	 * @see org.openstreetmap.josm.gui.PleaseWaitRunnable#realRun()
	 */
	@Override
	protected void realRun() throws SAXException, IOException,
			OsmTransferException {
		this.progressMonitor.setTicksCount(9);
		this.progressMonitor.setTicks(0);

		// get layer data
		Layer layer = Main.main.getActiveLayer();

		// scenario for converted data
		Scenario sourceScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		sourceScenario.getConfig().scenario().setUseTransit(true);
		sourceScenario.getConfig().scenario().setUseVehicles(true);

		this.progressMonitor.setTicks(1);
		this.progressMonitor.setCustomText("converting osm data..");

		// convert layer data
        NetworkListener networkListener = new NetworkListener(sourceScenario, new HashMap<Way, List<Link>>(), new HashMap<Link, List<WaySegment>>(), new HashMap<Relation, TransitRoute>());
        networkListener.visitAll(((OsmDataLayer) layer).data);

        // check if network should be cleaned
		if (Main.pref.getBoolean("matsim_cleanNetwork")) {
			this.progressMonitor.setTicks(2);
			this.progressMonitor.setCustomText("cleaning network..");
			new NetworkCleaner().run(sourceScenario.getNetwork());
		}
		this.progressMonitor.setTicks(3);
		this.progressMonitor.setCustomText("preparing data set..");
        Importer importer = new Importer(sourceScenario, Main.getProjection());
        importer.run();
        newLayer = importer.getLayer();
    }

	/**
	 * @see org.openstreetmap.josm.gui.PleaseWaitRunnable#finish()
	 */
	@Override
	protected void finish() {
		if (newLayer != null) {
			Main.main.addLayer(newLayer);
			Main.map.mapView.setActiveLayer(newLayer);
		}
	}

}
