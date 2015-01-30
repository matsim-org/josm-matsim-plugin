package org.matsim.contrib.josm;

import java.io.File;

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
import org.matsim.core.scenario.ScenarioUtils;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

/**
 * The task which which writes out the network xml file
 * 
 * @author Nico
 * 
 */

class ExportTask {

	private final File networkFile;
	private OsmDataLayer layer;

	/**
	 * Creates a new Export task with the given export <code>file</code>
	 * location
	 * 
	 * @param file
	 *            The file to be exported to
	 */
	public ExportTask(File file, OsmDataLayer layer) {
		this.networkFile = file;
	}

	protected void realRun() {

		// create empty data structures
		Config config = ConfigUtils.createConfig();
		Scenario sc = ScenarioUtils.createScenario(config);
		Network network = sc.getNetwork();
		config.scenario().setUseTransit(true);
		config.scenario().setUseVehicles(true);

		// copy nodes with switched id fields
		for (Node node : ((MATSimLayer) layer).getMatsimScenario().getNetwork()
				.getNodes().values()) {
			Node newNode = network.getFactory().createNode(
					Id.create(((NodeImpl) node).getOrigId(), Node.class),
					node.getCoord());
			network.addNode(newNode);
		}
		// copy links with switched id fields
		for (Link link : ((MATSimLayer) layer).getMatsimScenario().getNetwork()
				.getLinks().values()) {
			Link newLink = network
					.getFactory()
					.createLink(
							Id.create(((LinkImpl) link).getOrigId(), Link.class),
							network.getNodes().get(
									Id.create(((NodeImpl) link.getFromNode())
											.getOrigId(), Link.class)),
							network.getNodes().get(
									Id.create(((NodeImpl) link.getToNode())
											.getOrigId(), Node.class)));
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