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
import org.openstreetmap.josm.command.MoveCommand;
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
		way.put("freespeed", "10.0");
		way.put("capacity", "1000.0");
		way.put("permlanes", "1.0");
		way.put("modes", "car");
		new AddCommand(matsimLayer, way).executeCommand();
		Assert.assertEquals(1, matsimLayer.getScenario().getNetwork().getLinks().size());
	}

	@Test
	public void createLinkUndo() {
		MATSimLayer matsimLayer = NewNetworkAction.createMatsimLayer();
		Main.main.addLayer(matsimLayer);
		Node node1 = new Node();
		node1.setCoor(new LatLon(0.0, 0.0));
		AddCommand addNode1 = new AddCommand(matsimLayer, node1);
		addNode1.executeCommand();
		Node node2 = new Node();
		node2.setCoor(new LatLon(0.1, 0.1));
		AddCommand addNode2 = new AddCommand(matsimLayer, node2);
		addNode2.executeCommand();
		Way way = new Way();
		way.addNode(node1);
		way.addNode(node2);
		way.put("freespeed", "10.0");
		way.put("capacity", "1000.0");
		way.put("permlanes", "1.0");
		way.put("modes", "car");
		AddCommand addWay = new AddCommand(matsimLayer, way);
		addWay.executeCommand();
		Assert.assertEquals(1, matsimLayer.getScenario().getNetwork().getLinks().size());
		Assert.assertEquals(2, matsimLayer.getScenario().getNetwork().getNodes().size());
		addWay.undoCommand();
		Assert.assertEquals(0, matsimLayer.getScenario().getNetwork().getLinks().size());
		addNode2.undoCommand();
		Assert.assertEquals(1, matsimLayer.getScenario().getNetwork().getNodes().size());
		addNode1.undoCommand();
		Assert.assertEquals(0, matsimLayer.getScenario().getNetwork().getNodes().size());
	}


	@Test
	public void createLinkDeleteUndoDelete() {
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
		way.put("freespeed", "10.0");
		way.put("capacity", "1000.0");
		way.put("permlanes", "1.0");
		way.put("modes", "car");
		new AddCommand(matsimLayer, way).executeCommand();
		DeleteCommand delete = new DeleteCommand(way);
		delete.executeCommand();
		delete.undoCommand();
		Assert.assertEquals(1, matsimLayer.getScenario().getNetwork().getLinks().size());
	}

	@Test
	public void createLinkDeleteWithNodesUndoDelete() {
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
		way.put("freespeed", "10.0");
		way.put("capacity", "1000.0");
		way.put("permlanes", "1.0");
		way.put("modes", "car");
		new AddCommand(matsimLayer, way).executeCommand();
		DeleteCommand deleteWay = new DeleteCommand(way);
		deleteWay.executeCommand();
		Assert.assertEquals(0, matsimLayer.getScenario().getNetwork().getLinks().size());
		Assert.assertEquals(0, matsimLayer.getScenario().getNetwork().getNodes().size());
		DeleteCommand deleteNode1 = new DeleteCommand(node1);
		deleteNode1.executeCommand();
		DeleteCommand deleteNode2 = new DeleteCommand(node2);
		deleteNode2.executeCommand();
		deleteNode2.undoCommand();
		deleteNode1.undoCommand();
		// These nodes are not needed for a link yet:
		Assert.assertEquals(0, matsimLayer.getScenario().getNetwork().getNodes().size());
		deleteWay.undoCommand();
		Assert.assertEquals(1, matsimLayer.getScenario().getNetwork().getLinks().size());
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

	@Test
	public void wiggleRoadWithTransitStop() {
		Preferences.setTransitLite(true);
		MATSimLayer matsimLayer = PtTutorialScenario.layer();
		Main.main.addLayer(matsimLayer);
		Assert.assertEquals(4, matsimLayer.getScenario().getTransitSchedule().getFacilities().size());

		Node node2 = findNode2(matsimLayer);
		new MoveCommand(node2, new LatLon(node2.getCoor().lat()+0.01, node2.getCoor().lon()+0.01)).executeCommand();
		Assert.assertEquals(4, matsimLayer.getScenario().getTransitSchedule().getFacilities().size());
	}

	private Node findNode2(MATSimLayer matsimLayer) {
		for (Node node : matsimLayer.data.getNodes()) {
			if ("2".equals(node.get("id"))) {
				return node;
			}
		}
		throw new RuntimeException("Where is node 2?");
	}

}
