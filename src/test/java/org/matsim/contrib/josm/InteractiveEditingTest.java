package org.matsim.contrib.josm;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;

public class InteractiveEditingTest {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Before
	public void init() {
		new JOSMFixture(folder.getRoot().getPath()).init(true);
	}

	@Test
	public void createLink() {
		MATSimLayer matsimLayer = NewNetworkAction.createMatsimLayer();
		Main.main.addLayer(matsimLayer);
		Node node1 = new Node();
		node1.setCoor(new LatLon(0.0, 0.0));
		new AddCommand(matsimLayer, node1).executeCommand();
		Node node2 = new Node();
		node2.setCoor(new LatLon(0.1, 0.1));
		new AddCommand(matsimLayer, node2).executeCommand();
		Way way = new Way();
		way.addNode(node1);
		way.addNode(node2);
		way.getKeys().put("freespeed", "10.0");
		way.getKeys().put("capacity", "1000.0");
		way.getKeys().put("permlanes", "1.0");
		way.getKeys().put("modes", "car");
		new AddCommand(matsimLayer, way).executeCommand();
		Assert.assertEquals(1, matsimLayer.getScenario().getNetwork().getLinks().size());
		DeleteCommand delete = new DeleteCommand(way);
		delete.executeCommand();
		Assert.assertEquals(0, matsimLayer.getScenario().getNetwork().getLinks().size());
	}


	@Test
	public void createLinkThenSetMatsimAttribtues() {
		MATSimLayer matsimLayer = NewNetworkAction.createMatsimLayer();
		Main.main.addLayer(matsimLayer);
		Node node1 = new Node();
		node1.setCoor(new LatLon(0.0, 0.0));
		new AddCommand(matsimLayer, node1).executeCommand();
		Node node2 = new Node();
		node2.setCoor(new LatLon(0.1, 0.1));
		new AddCommand(matsimLayer, node2).executeCommand();
		Way way = new Way();
		way.addNode(node1);
		way.addNode(node2);
		new AddCommand(matsimLayer, way).executeCommand();
		Assert.assertEquals(0, matsimLayer.getScenario().getNetwork().getLinks().size());
		new ChangePropertyCommand(way, "freespeed", "10.0").executeCommand();
		new ChangePropertyCommand(way, "capacity", "1000.0").executeCommand();
		new ChangePropertyCommand(way, "permlanes", "1.0").executeCommand();
		new ChangePropertyCommand(way, "modes", "car").executeCommand();
		Assert.assertEquals(1, matsimLayer.getScenario().getNetwork().getLinks().size());
	}

}
