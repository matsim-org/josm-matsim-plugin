package org.matsim.contrib.josm;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.josm.gui.Preferences;
import org.matsim.contrib.josm.model.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.openstreetmap.josm.command.ChangeNodesCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.io.Compression;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class OSMDataTest {


	@Rule
	public JOSMTestRules test = new JOSMTestRules().preferences().projection();

	private OsmDataLayer incompleteWayLayer;
	private NetworkModel incompleteWayListener;
	private OsmDataLayer busRouteLayer;
	private NetworkModel busRouteListener;
	private OsmDataLayer intersectionsLayer;
	private NetworkModel intersectionsListener;

	@Before
	public void init() throws IOException, IllegalDataException {
		Preferences.setSupportTransit(true);
		URL urlIncompleteWay = getClass().getResource("/test-input/OSMData/incompleteWay.osm.xml");
		URL urlRoute = getClass().getResource("/test-input/OSMData/busRoute.osm.xml");
		URL urlIntersections = getClass().getResource("/test-input/OSMData/loops_intersecting_ways.osm.xml");

		InputStream incompleteWayInput;
		InputStream busRouteInput;
		InputStream intersectionsInput;
		incompleteWayInput = Compression.getUncompressedFileInputStream(new File(urlIncompleteWay.getFile()));
		busRouteInput = Compression.getUncompressedFileInputStream(new File(urlRoute.getFile()));
		intersectionsInput = Compression.getUncompressedFileInputStream(new File(urlIntersections.getFile()));
		DataSet incompleteWayData;
		DataSet busRouteData;
		DataSet intersectionsData;
		incompleteWayData = OsmReader.parseDataSet(incompleteWayInput, null);
		busRouteData = OsmReader.parseDataSet(busRouteInput, null);
		intersectionsData = OsmReader.parseDataSet(intersectionsInput, null);
		incompleteWayLayer = new OsmDataLayer(incompleteWayData, "test", null);
		busRouteLayer = new OsmDataLayer(busRouteData, "test", null);
		intersectionsLayer = new OsmDataLayer(intersectionsData, "test", null);
		Config config = ConfigUtils.createConfig();
		config.transit().setUseTransit(true);
		busRouteListener = NetworkModel.createNetworkModel(busRouteData);
		busRouteListener.visitAll();
		incompleteWayListener = NetworkModel.createNetworkModel(incompleteWayData);
		incompleteWayListener.visitAll();
		intersectionsListener = NetworkModel.createNetworkModel(intersectionsData);
		intersectionsListener.visitAll();
	}


	@Test
	public void testWayNodesChanged() {
		org.openstreetmap.josm.spi.preferences.Config.getPref().putBoolean("matsim_keepPaths", true);
		Way way = incompleteWayLayer.data.getWays().iterator().next();
		List<Node> nodes = new ArrayList<>();
		for (Node node: incompleteWayLayer.data.getNodes()) {
			if(!node.isIncomplete()) {
				nodes.add(node);
			}
		}

		Assert.assertEquals(0,incompleteWayListener.nodes().size());
		Command changeNodes = new ChangeNodesCommand(way, nodes);
		changeNodes.executeCommand();
		Assert.assertEquals(1,incompleteWayLayer.data.getWays().size());
		Assert.assertEquals(4,incompleteWayListener.nodes().size());
		Assert.assertEquals(6,incompleteWayListener.getWay2Links().values().stream().mapToInt(List::size).sum());

		nodes.remove(way.lastNode());
		Command changeNodes2 = new ChangeNodesCommand(way, nodes);
		changeNodes2.executeCommand();

		Assert.assertEquals(1,incompleteWayLayer.data.getWays().size());
		Assert.assertEquals(3,incompleteWayListener.nodes().size());
		Assert.assertEquals(4,incompleteWayListener.getWay2Links().values().stream().mapToInt(List::size).sum());

		changeNodes2.undoCommand();
		Assert.assertEquals(4,incompleteWayListener.nodes().size());
		Assert.assertEquals(6,incompleteWayListener.getWay2Links().values().stream().mapToInt(List::size).sum());

		changeNodes.undoCommand();
		Assert.assertEquals(0,incompleteWayListener.nodes().size());
		Assert.assertEquals(0,incompleteWayListener.getWay2Links().values().stream().mapToInt(List::size).sum());
	}


	@Test
	public void testIncompleteWay() {
		Assert.assertEquals(1,incompleteWayLayer.data.getWays().size());
		Assert.assertEquals(0,incompleteWayListener.getWay2Links().values().stream().mapToInt(List::size).sum());
		Assert.assertEquals(0,incompleteWayListener.nodes().size());

		MATSimLayer matsimLayer = LayerConverter.convertWithFullTransit(incompleteWayLayer);
		Assert.assertEquals(0, matsimLayer.data.getWays().size());
		Assert.assertEquals(0, matsimLayer.getNetworkModel().getWay2Links().values().stream().mapToInt(List::size).sum());

	}

	@Test
	public void testBusRoute() {
		Preferences.setSupportTransit(true);

		Assert.assertEquals(10,busRouteListener.getWay2Links().values().stream().mapToInt(List::size).sum());
		Assert.assertEquals(6,busRouteListener.nodes().size());
		Assert.assertEquals(4,busRouteListener.stopAreas().size());
		Assert.assertEquals(1,busRouteListener.lines().size());
		Assert.assertEquals(2,countRoutes(busRouteListener));
		for (Route route: busRouteListener.lines().values().iterator().next().getRoutes()) {
			Assert.assertEquals(4, route.getStops().size());
			Assert.assertEquals(5, route.getRoute().size());
		}

		org.openstreetmap.josm.spi.preferences.Config.getPref().putBoolean("matsim_keepPaths", true);
		Assert.assertEquals(18,busRouteListener.getWay2Links().values().stream().mapToInt(List::size).sum());
		Assert.assertEquals(10,busRouteListener.nodes().size());
		Assert.assertEquals(4,busRouteListener.stopAreas().size());
		Assert.assertEquals(1,busRouteListener.lines().size());
		Assert.assertEquals(2,countRoutes(busRouteListener));
		for (Route route: busRouteListener.lines().values().iterator().next().getRoutes()) {
			Assert.assertEquals(4, route.getStops().size());
			Assert.assertEquals(9, route.getRoute().size());
		}

		Scenario scenario = Export.toScenario(busRouteListener);
		int nStopsWithLink = 0;
		for (TransitStopFacility transitStopFacility : scenario.getTransitSchedule().getFacilities().values()) {
			if (transitStopFacility.getLinkId() != null) {
				nStopsWithLink++;
			}
		}
		Assert.assertEquals(1, nStopsWithLink);

		org.openstreetmap.josm.spi.preferences.Config.getPref().putBoolean("matsim_keepPaths", false);
		Assert.assertEquals(10,busRouteListener.getWay2Links().values().stream().mapToInt(List::size).sum());
		Assert.assertEquals(6,busRouteListener.nodes().size());
		Assert.assertEquals(4,busRouteListener.stopAreas().size());
		Assert.assertEquals(1,busRouteListener.lines().size());
		Assert.assertEquals(2,countRoutes(busRouteListener));
		for (Route route: busRouteListener.lines().values().iterator().next().getRoutes()) {
			Assert.assertEquals(4, route.getStops().size());
			Assert.assertEquals(5, route.getRoute().size());
		}

		scenario = Export.toScenario(busRouteListener);
		Assert.assertEquals("busRoute", scenario.getTransitSchedule().getTransitLines().values().iterator().next().getId().toString());

		Assert.assertEquals(10, scenario.getNetwork().getLinks().size());
		Assert.assertEquals(6, scenario.getNetwork().getNodes().size());
		Assert.assertEquals(4, scenario.getTransitSchedule().getFacilities().size());
		Assert.assertEquals(1, scenario.getTransitSchedule().getTransitLines().size());
		Assert.assertEquals(2, countRoutes(scenario.getTransitSchedule()));

		TransitRoute route1to4 = scenario.getTransitSchedule().getTransitLines().get(Id.create("busRoute", TransitLine.class)).getRoutes().get(Id.create("1to4", TransitRoute.class));
		Assert.assertEquals(4, route1to4.getStops().size());
		Assert.assertEquals(4, route1to4.getRoute().getLinkIds().size()); // this one needs a dummy entry link

		TransitRoute route4to1 = scenario.getTransitSchedule().getTransitLines().get(Id.create("busRoute", TransitLine.class)).getRoutes().get(Id.create("4to1", TransitRoute.class));
		Assert.assertEquals(4, route4to1.getStops().size());
		Assert.assertEquals(3, route4to1.getRoute().getLinkIds().size());


		MATSimLayer matsimLayer = LayerConverter.convertWithFullTransit(busRouteLayer);
		DataSet convertedOsm = matsimLayer.data;
		Assert.assertEquals(10, convertedOsm.getNodes().size());
		Assert.assertEquals(10, convertedOsm.getWays().size());
		Assert.assertEquals(7, convertedOsm.getRelations().size());

		scenario = Export.toScenario(matsimLayer.getNetworkModel());
		Assert.assertEquals("busRoute", scenario.getTransitSchedule().getTransitLines().values().iterator().next().getId().toString());

	}

	@Test
	public void testIntersections() {
		 Assert.assertEquals(11,intersectionsListener.getWay2Links().values().stream().mapToInt(List::size).sum());
		 Assert.assertEquals(12,intersectionsListener.nodes().size());

		 Command delete = DeleteCommand.delete(Collections.singleton(intersectionsLayer.data.getPrimitiveById(14, OsmPrimitiveType.NODE)), false, true);
		 delete.executeCommand();
		 Assert.assertEquals(9,intersectionsListener.getWay2Links().values().stream().mapToInt(List::size).sum());
		 Assert.assertEquals(11,intersectionsListener.nodes().size());

		 delete.undoCommand();
		 Assert.assertEquals(11,intersectionsListener.getWay2Links().values().stream().mapToInt(List::size).sum());
		 Assert.assertEquals(12,intersectionsListener.nodes().size());

		 Command delete2 = DeleteCommand.delete(Collections.singleton(intersectionsLayer.data.getPrimitiveById(2, OsmPrimitiveType.NODE)), false, true);
		 delete2.executeCommand();
		 Assert.assertEquals(10,intersectionsListener.getWay2Links().values().stream().mapToInt(List::size).sum());
		 Assert.assertEquals(12,intersectionsListener.nodes().size());

		 delete2.undoCommand();
		 Assert.assertEquals(11,intersectionsListener.getWay2Links().values().stream().mapToInt(List::size).sum());
		 Assert.assertEquals(12,intersectionsListener.nodes().size());

		 Command delete3 = DeleteCommand.delete(Collections.singleton(intersectionsLayer.data.getPrimitiveById(3, OsmPrimitiveType.WAY)), false, true);
		 delete3.executeCommand();
		 Assert.assertEquals(8,intersectionsListener.getWay2Links().values().stream().mapToInt(List::size).sum());
		 Assert.assertEquals(9,intersectionsListener.nodes().size());

		 delete3.undoCommand();
		 Assert.assertEquals(11,intersectionsListener.getWay2Links().values().stream().mapToInt(List::size).sum());
		 Assert.assertEquals(12,intersectionsListener.nodes().size());
	}

    private long countRoutes(NetworkModel transitSchedule) {
        int result = 0;
        for (Line transitLine : transitSchedule.lines().values()) {
            result += transitLine.getRoutes().size();
        }
        return result;
    }

	private long countRoutes(TransitSchedule transitSchedule) {
		int result = 0;
		for (TransitLine transitLine : transitSchedule.getTransitLines().values()) {
			result += transitLine.getRoutes().size();
		}
		return result;
	}


}
