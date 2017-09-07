package org.matsim.contrib.josm;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.contrib.josm.actions.NewNetworkAction;
import org.matsim.contrib.josm.gui.Preferences;
import org.matsim.contrib.josm.model.LinkConversionRules;
import org.matsim.contrib.josm.model.MATSimLayer;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.command.MoveCommand;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import java.util.List;

public class InteractiveEditingTest {

	@Rule
	public JOSMTestRules test = new JOSMTestRules().preferences();
	

	@Test
	public void createLink() {
		MATSimLayer matsimLayer = NewNetworkAction.createMatsimLayer();
		MainApplication.getLayerManager().addLayer(matsimLayer);
		Node node1 = new Node();
		node1.setCoor(new LatLon(0.0, 0.0));
		new AddCommand(matsimLayer, node1).executeCommand();
		Node node2 = new Node();
		node2.setCoor(new LatLon(0.1, 0.1));
		new AddCommand(matsimLayer, node2).executeCommand();
		Way way = new Way();
		way.addNode(node1);
		way.addNode(node2);
		way.put(LinkConversionRules.FREESPEED, "10.0");
		way.put(LinkConversionRules.CAPACITY, "1000.0");
		way.put(LinkConversionRules.PERMLANES, "1.0");
		way.put(LinkConversionRules.MODES, "car");
		new AddCommand(matsimLayer, way).executeCommand();
		Assert.assertEquals(1, matsimLayer.getNetworkModel().getWay2Links().values().stream().mapToInt(List::size).sum());
	}

	@Test
	public void createLinkUndo() {
		MATSimLayer matsimLayer = NewNetworkAction.createMatsimLayer();
		MainApplication.getLayerManager().addLayer(matsimLayer);
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
		way.put(LinkConversionRules.FREESPEED, "10.0");
		way.put(LinkConversionRules.CAPACITY, "1000.0");
		way.put(LinkConversionRules.PERMLANES, "1.0");
		way.put(LinkConversionRules.MODES, "car");
		AddCommand addWay = new AddCommand(matsimLayer, way);
		addWay.executeCommand();
		Assert.assertEquals(1, matsimLayer.getNetworkModel().getWay2Links().values().stream().mapToInt(List::size).sum());
		Assert.assertEquals(2, matsimLayer.getNetworkModel().nodes().size());
		addWay.undoCommand();
		Assert.assertEquals(0, matsimLayer.getNetworkModel().getWay2Links().values().stream().mapToInt(List::size).sum());
		addNode2.undoCommand();
		Assert.assertEquals(1, matsimLayer.getNetworkModel().nodes().size());
		addNode1.undoCommand();
		Assert.assertEquals(0, matsimLayer.getNetworkModel().nodes().size());
	}


	@Test
	public void createLinkDeleteUndoDelete() {
		MATSimLayer matsimLayer = NewNetworkAction.createMatsimLayer();
		MainApplication.getLayerManager().addLayer(matsimLayer);
		Node node1 = new Node();
		node1.setCoor(new LatLon(0.0, 0.0));
		new AddCommand(matsimLayer, node1).executeCommand();
		Node node2 = new Node();
		node2.setCoor(new LatLon(0.1, 0.1));
		new AddCommand(matsimLayer, node2).executeCommand();
		Way way = new Way();
		way.addNode(node1);
		way.addNode(node2);
		way.put(LinkConversionRules.FREESPEED, "10.0");
		way.put(LinkConversionRules.CAPACITY, "1000.0");
		way.put(LinkConversionRules.PERMLANES, "1.0");
		way.put(LinkConversionRules.MODES, "car");
		new AddCommand(matsimLayer, way).executeCommand();
		DeleteCommand delete = new DeleteCommand(way);
		delete.executeCommand();
		delete.undoCommand();
		Assert.assertEquals(1, matsimLayer.getNetworkModel().getWay2Links().values().stream().mapToInt(List::size).sum());
	}

	@Test
	public void createLinkDeleteWithNodesUndoDelete() {
		MATSimLayer matsimLayer = NewNetworkAction.createMatsimLayer();
		MainApplication.getLayerManager().addLayer(matsimLayer);
		Node node1 = new Node();
		node1.setCoor(new LatLon(0.0, 0.0));
		new AddCommand(matsimLayer, node1).executeCommand();
		Node node2 = new Node();
		node2.setCoor(new LatLon(0.1, 0.1));
		new AddCommand(matsimLayer, node2).executeCommand();
		Way way = new Way();
		way.addNode(node1);
		way.addNode(node2);
		way.put(LinkConversionRules.FREESPEED, "10.0");
		way.put(LinkConversionRules.CAPACITY, "1000.0");
		way.put(LinkConversionRules.PERMLANES, "1.0");
		way.put(LinkConversionRules.MODES, "car");
		new AddCommand(matsimLayer, way).executeCommand();
		DeleteCommand deleteWay = new DeleteCommand(way);
		deleteWay.executeCommand();
		Assert.assertEquals(0, matsimLayer.getNetworkModel().getWay2Links().values().stream().mapToInt(List::size).sum());
		Assert.assertEquals(0, matsimLayer.getNetworkModel().nodes().size());
		DeleteCommand deleteNode1 = new DeleteCommand(node1);
		deleteNode1.executeCommand();
		DeleteCommand deleteNode2 = new DeleteCommand(node2);
		deleteNode2.executeCommand();
		deleteNode2.undoCommand();
		deleteNode1.undoCommand();
		// These nodes are not needed for a link yet:
		Assert.assertEquals(0, matsimLayer.getNetworkModel().nodes().size());
		deleteWay.undoCommand();
		Assert.assertEquals(1, matsimLayer.getNetworkModel().getWay2Links().values().stream().mapToInt(List::size).sum());
	}


	@Test
	public void createLinkThenSetMatsimAttribtues() {
		MATSimLayer matsimLayer = NewNetworkAction.createMatsimLayer();
		MainApplication.getLayerManager().addLayer(matsimLayer);
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
		Assert.assertEquals(0, matsimLayer.getNetworkModel().getWay2Links().values().stream().mapToInt(List::size).sum());
		new ChangePropertyCommand(way, LinkConversionRules.FREESPEED, "10.0").executeCommand();
		new ChangePropertyCommand(way, LinkConversionRules.CAPACITY, "1000.0").executeCommand();
		new ChangePropertyCommand(way, LinkConversionRules.PERMLANES, "1.0").executeCommand();
		new ChangePropertyCommand(way, LinkConversionRules.MODES, "car").executeCommand();
		Assert.assertEquals(1, matsimLayer.getNetworkModel().getWay2Links().values().stream().mapToInt(List::size).sum());
	}

	@Test
	public void wiggleRoadWithTransitStop() {
		Preferences.setSupportTransit(true);
		Preferences.setTransitLite(true);
		MATSimLayer matsimLayer = PtTutorialScenario.layer();
		MainApplication.getLayerManager().addLayer(matsimLayer);
		Assert.assertEquals(4, matsimLayer.getNetworkModel().stopAreas().size());

		Node node2 = findNode2(matsimLayer);
		new MoveCommand(node2, new LatLon(node2.getCoor().lat()+0.01, node2.getCoor().lon()+0.01)).executeCommand();
		Assert.assertEquals(4, matsimLayer.getNetworkModel().stopAreas().size());
	}

	private Node findNode2(MATSimLayer matsimLayer) {
		for (Node node : matsimLayer.data.getNodes()) {
			if ("2".equals(node.get("matsim:id"))) {
				return node;
			}
		}
		throw new RuntimeException("Where is node 2?");
	}

}
