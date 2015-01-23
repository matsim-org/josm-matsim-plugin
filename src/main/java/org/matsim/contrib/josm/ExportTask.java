package org.matsim.contrib.josm;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.LinkImpl;
import org.matsim.core.network.NetworkWriter;
import org.matsim.core.network.NodeImpl;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.population.routes.LinkNetworkRouteImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.UncheckedIOException;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.io.OsmTransferException;
import org.xml.sax.SAXException;

/**
 * The task which which writes out the network xml file
 * 
 * @author Nico
 * 
 */

class ExportTask {

	private final File networkFile;

	/**
	 * Creates a new Export task with the given export <code>file</code>
	 * location
	 * 
	 * @param file
	 *            The file to be exported to
	 */
	public ExportTask(File file) {
		this.networkFile = file;
	}

	protected void realRun() {

		// create empty data structures
		Config config = ConfigUtils.createConfig();
		Scenario sc = ScenarioUtils.createScenario(config);
		Network network = sc.getNetwork();
		config.scenario().setUseTransit(true);
		config.scenario().setUseVehicles(true);
		Layer layer = Main.main.getActiveLayer();

		if (layer instanceof OsmDataLayer) {

            // copy nodes with switched id fields
            for (Node node : ((MATSimLayer) layer).getMatsimScenario()
                    .getNetwork().getNodes().values()) {
                Node newNode = network.getFactory()
                        .createNode(
                                Id.create(((NodeImpl) node).getOrigId(),
                                        Node.class), node.getCoord());
                network.addNode(newNode);
            }
            // copy links with switched id fields
            for (Link link : ((MATSimLayer) layer).getMatsimScenario()
                    .getNetwork().getLinks().values()) {
                Link newLink = network.getFactory()
                        .createLink(
                                Id.create(((LinkImpl) link).getOrigId(),
                                        Link.class),
                                network.getNodes().get(
                                        Id.create(
                                                ((NodeImpl) link
                                                        .getFromNode())
                                                        .getOrigId(),
                                                Link.class)),
                                network.getNodes().get(
                                        Id.create(((NodeImpl) link
                                                        .getToNode()).getOrigId(),
                                                Node.class)));
                newLink.setFreespeed(link.getFreespeed());
                newLink.setCapacity(link.getCapacity());
                newLink.setLength(link.getLength());
                newLink.setNumberOfLanes(link.getNumberOfLanes());
                newLink.setAllowedModes(link.getAllowedModes());
                network.addLink(newLink);
            }

			// check for network cleaner
			if (Main.pref.getBoolean("matsim_cleanNetwork")) {
				new NetworkCleaner().run(network);
			}
            System.out.println(networkFile.getPath());
			// write out paths
			new NetworkWriter(network).write(networkFile.getPath());
			
		}
	}
}