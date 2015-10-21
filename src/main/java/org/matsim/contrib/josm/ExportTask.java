package org.matsim.contrib.josm;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.LinkImpl;
import org.matsim.core.network.NetworkWriter;
import org.matsim.core.network.NodeImpl;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.scenario.ScenarioUtils;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

import java.io.File;

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
	this.layer = layer;
    }

    protected void realRun() {
	Scenario layerScenario = ((MATSimLayer) layer).getScenario();
	Scenario targetScenario = convertIDs(layerScenario);

	if (Main.pref.getBoolean("matsim_cleanNetwork")) {
	    new NetworkCleaner().run(targetScenario.getNetwork());
	}
	new NetworkWriter(targetScenario.getNetwork()).write(networkFile.getPath());
    }

    static Scenario convertIDs(Scenario layerScenario) {
	Scenario sc = ScenarioUtils.createScenario(layerScenario.getConfig());

	// copy nodes with switched id fields
	for (Node node : layerScenario.getNetwork().getNodes().values()) {
	    Node newNode = sc.getNetwork().getFactory().createNode(Id.create(((NodeImpl) node).getOrigId(), Node.class), node.getCoord());
	    sc.getNetwork().addNode(newNode);
	}
	// copy links with switched id fields
	for (Link link : layerScenario.getNetwork().getLinks().values()) {
	    Link newLink = sc
		    .getNetwork()
		    .getFactory()
		    .createLink(Id.create(((LinkImpl) link).getOrigId(), Link.class),
			    sc.getNetwork().getNodes().get(Id.create(((NodeImpl) link.getFromNode()).getOrigId(), Node.class)),
			    sc.getNetwork().getNodes().get(Id.create(((NodeImpl) link.getToNode()).getOrigId(), Node.class)));
	    newLink.setFreespeed(link.getFreespeed());
	    newLink.setCapacity(link.getCapacity());
	    newLink.setLength(link.getLength());
	    newLink.setNumberOfLanes(link.getNumberOfLanes());
	    newLink.setAllowedModes(link.getAllowedModes());
	    sc.getNetwork().addLink(newLink);
	}
	return sc;
    }
}