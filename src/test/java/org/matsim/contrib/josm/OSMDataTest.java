package org.matsim.contrib.josm;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.josm.scenario.EditableScenario;
import org.matsim.contrib.josm.scenario.EditableScenarioUtils;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.ChangeNodesCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.io.Compression;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.testutils.JOSMTestRules;


public class OSMDataTest {


	@Rule
	public JOSMTestRules test = new JOSMTestRules().preferences().projection();

	private OsmDataLayer incompleteWayLayer;
	private NetworkListener incompleteWayListener;
	private OsmDataLayer busRouteLayer;
	private NetworkListener busRouteListener;
	private OsmDataLayer intersectionsLayer;
	private NetworkListener intersectionsListener;

	@Before
	public void init() throws IOException, IllegalDataException {
		Main.pref.put("matsim_supportTransit", true);
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
		busRouteListener = new NetworkListener(busRouteData, EditableScenarioUtils.createScenario(config), new HashMap<Way, List<Link>>(), new HashMap<Link, List<WaySegment>>(), new HashMap<Relation, TransitStopFacility>());
		Main.pref.addPreferenceChangeListener(busRouteListener);
		busRouteListener.visitAll();
		incompleteWayListener = new NetworkListener(incompleteWayData, EditableScenarioUtils.createScenario(config), new HashMap<Way, List<Link>>(), new HashMap<Link, List<WaySegment>>(), new HashMap<Relation, TransitStopFacility>());
		incompleteWayListener.visitAll();
		intersectionsListener = new NetworkListener(intersectionsData, EditableScenarioUtils.createScenario(config), new HashMap<Way, List<Link>>(), new HashMap<Link, List<WaySegment>>(), new HashMap<Relation, TransitStopFacility>());
		Main.pref.addPreferenceChangeListener(intersectionsListener);
		intersectionsListener.visitAll();
		busRouteData.addDataSetListener(busRouteListener);
		incompleteWayData.addDataSetListener(incompleteWayListener);
		intersectionsData.addDataSetListener(intersectionsListener);
	}


	@Test
	public void testWayNodesChanged() {
		Main.pref.put("matsim_keepPaths", true);
		Way way = incompleteWayLayer.data.getWays().iterator().next();
		List<Node> nodes = new ArrayList<>();
		for (Node node: incompleteWayLayer.data.getNodes()) {
			if(!node.isIncomplete()) {
				nodes.add(node);
			}
		}

		Assert.assertEquals(0,incompleteWayListener.getScenario().getNetwork().getNodes().size());
		Command changeNodes = new ChangeNodesCommand(way, nodes);
		changeNodes.executeCommand();
		Assert.assertEquals(1,incompleteWayLayer.data.getWays().size());
		Assert.assertEquals(4,incompleteWayListener.getScenario().getNetwork().getNodes().size());
		Assert.assertEquals(6,incompleteWayListener.getScenario().getNetwork().getLinks().size());

		nodes.remove(way.lastNode());
		Command changeNodes2 = new ChangeNodesCommand(way, nodes);
		changeNodes2.executeCommand();

		Assert.assertEquals(1,incompleteWayLayer.data.getWays().size());
		Assert.assertEquals(3,incompleteWayListener.getScenario().getNetwork().getNodes().size());
		Assert.assertEquals(4,incompleteWayListener.getScenario().getNetwork().getLinks().size());

		changeNodes2.undoCommand();
		Assert.assertEquals(4,incompleteWayListener.getScenario().getNetwork().getNodes().size());
		Assert.assertEquals(6,incompleteWayListener.getScenario().getNetwork().getLinks().size());

		changeNodes.undoCommand();
		Assert.assertEquals(0,incompleteWayListener.getScenario().getNetwork().getNodes().size());
		Assert.assertEquals(0,incompleteWayListener.getScenario().getNetwork().getLinks().size());
	}


	@Test
	public void testIncompleteWay() {
		Assert.assertEquals(1,incompleteWayLayer.data.getWays().size());
		Assert.assertEquals(0,incompleteWayListener.getScenario().getNetwork().getLinks().size());
		Assert.assertEquals(0,incompleteWayListener.getScenario().getNetwork().getNodes().size());

		LayerConverter converter =  new LayerConverter(incompleteWayLayer);
		converter.run();

		Assert.assertEquals(0,converter.getMatsimLayer().data.getWays().size());
		Assert.assertEquals(0,converter.getMatsimLayer().getScenario().getNetwork().getLinks().size());

	}

	@Test
	public void testBusRoute() {
		Assert.assertEquals(10,busRouteListener.getScenario().getNetwork().getLinks().size());
		Assert.assertEquals(6,busRouteListener.getScenario().getNetwork().getNodes().size());
		Assert.assertEquals(4,busRouteListener.getScenario().getTransitSchedule().getFacilities().size());
		Assert.assertEquals(1,busRouteListener.getScenario().getTransitSchedule().getTransitLines().size());
		Assert.assertEquals(2,countRoutes(busRouteListener.getScenario().getTransitSchedule()));
		for (TransitRoute route: busRouteListener.getScenario().getTransitSchedule().getTransitLines().values().iterator().next().getRoutes().values()) {
			Assert.assertEquals(4, route.getStops().size());
			Assert.assertEquals(3, route.getRoute().getLinkIds().size());
		}

		Main.pref.put("matsim_keepPaths", true);
		Assert.assertEquals(18,busRouteListener.getScenario().getNetwork().getLinks().size());
		Assert.assertEquals(10,busRouteListener.getScenario().getNetwork().getNodes().size());
		Assert.assertEquals(4,busRouteListener.getScenario().getTransitSchedule().getFacilities().size());
		Assert.assertEquals(1,busRouteListener.getScenario().getTransitSchedule().getTransitLines().size());
		Assert.assertEquals(2,countRoutes(busRouteListener.getScenario().getTransitSchedule()));
		for (TransitRoute route: busRouteListener.getScenario().getTransitSchedule().getTransitLines().values().iterator().next().getRoutes().values()) {
			Assert.assertEquals(4, route.getStops().size());
			Assert.assertEquals(7, route.getRoute().getLinkIds().size());
		}

		Scenario simulatedExportScenario = ExportTask.convertIdsAndFilterDeleted((EditableScenario) busRouteListener.getScenario());
		int nStopsWithLink = 0;
		for (TransitStopFacility transitStopFacility : simulatedExportScenario.getTransitSchedule().getFacilities().values()) {
			if (transitStopFacility.getLinkId() != null) {
				nStopsWithLink++;
			}
		}
		Assert.assertEquals(1, nStopsWithLink);

		Main.pref.put("matsim_keepPaths", false);
		Assert.assertEquals(10,busRouteListener.getScenario().getNetwork().getLinks().size());
		Assert.assertEquals(6,busRouteListener.getScenario().getNetwork().getNodes().size());
		Assert.assertEquals(4,busRouteListener.getScenario().getTransitSchedule().getFacilities().size());
		Assert.assertEquals(1,busRouteListener.getScenario().getTransitSchedule().getTransitLines().size());
		Assert.assertEquals(2,countRoutes(busRouteListener.getScenario().getTransitSchedule()));
		for (TransitRoute route: busRouteListener.getScenario().getTransitSchedule().getTransitLines().values().iterator().next().getRoutes().values()) {
			Assert.assertEquals(4, route.getStops().size());
			Assert.assertEquals(3, route.getRoute().getLinkIds().size());
		}

		simulatedExportScenario = ExportTask.convertIdsAndFilterDeleted(((EditableScenario) busRouteListener.getScenario()));
		Assert.assertEquals("busRoute", simulatedExportScenario.getTransitSchedule().getTransitLines().values().iterator().next().getId().toString());



		LayerConverter converter =  new LayerConverter(busRouteLayer);
		converter.run();

		Scenario internalScenario = converter.getMatsimLayer().getScenario();
		Assert.assertEquals(10,internalScenario.getNetwork().getLinks().size());
		Assert.assertEquals(6,internalScenario.getNetwork().getNodes().size());
		Assert.assertEquals(4,internalScenario.getTransitSchedule().getFacilities().size());
		Assert.assertEquals(1,internalScenario.getTransitSchedule().getTransitLines().size());
		Assert.assertEquals(2,countRoutes(internalScenario.getTransitSchedule()));
		for (TransitRoute route: internalScenario.getTransitSchedule().getTransitLines().values().iterator().next().getRoutes().values()) {
			Assert.assertEquals(4, route.getStops().size());
			Assert.assertEquals(3, route.getRoute().getLinkIds().size());
		}

		DataSet convertedOsm = converter.getMatsimLayer().data;
		Assert.assertEquals(10, convertedOsm.getNodes().size());
		Assert.assertEquals(10, convertedOsm.getWays().size());
		Assert.assertEquals(7, convertedOsm.getRelations().size());

		simulatedExportScenario = ExportTask.convertIdsAndFilterDeleted(converter.getMatsimLayer().getScenario());
		Assert.assertEquals("busRoute", simulatedExportScenario.getTransitSchedule().getTransitLines().values().iterator().next().getId().toString());

	}

	@Test
	public void testIntersections() {
		 Assert.assertEquals(11,intersectionsListener.getScenario().getNetwork().getLinks().size());
		 Assert.assertEquals(12,intersectionsListener.getScenario().getNetwork().getNodes().size());

		 Command delete = DeleteCommand.delete(intersectionsLayer, Collections.singleton(intersectionsLayer.data.getPrimitiveById(14, OsmPrimitiveType.NODE)), false, true);
		 delete.executeCommand();
		 Assert.assertEquals(9,intersectionsListener.getScenario().getNetwork().getLinks().size());
		 Assert.assertEquals(11,intersectionsListener.getScenario().getNetwork().getNodes().size());

		 delete.undoCommand();
		 Assert.assertEquals(11,intersectionsListener.getScenario().getNetwork().getLinks().size());
		 Assert.assertEquals(12,intersectionsListener.getScenario().getNetwork().getNodes().size());

		 Command delete2 = DeleteCommand.delete(intersectionsLayer, Collections.singleton(intersectionsLayer.data.getPrimitiveById(2, OsmPrimitiveType.NODE)), false, true);
		 delete2.executeCommand();
		 Assert.assertEquals(10,intersectionsListener.getScenario().getNetwork().getLinks().size());
		 Assert.assertEquals(12,intersectionsListener.getScenario().getNetwork().getNodes().size());

		 delete2.undoCommand();
		 Assert.assertEquals(11,intersectionsListener.getScenario().getNetwork().getLinks().size());
		 Assert.assertEquals(12,intersectionsListener.getScenario().getNetwork().getNodes().size());

		 Command delete3 = DeleteCommand.delete(intersectionsLayer, Collections.singleton(intersectionsLayer.data.getPrimitiveById(3, OsmPrimitiveType.WAY)), false, true);
		 delete3.executeCommand();
		 Assert.assertEquals(8,intersectionsListener.getScenario().getNetwork().getLinks().size());
		 Assert.assertEquals(9,intersectionsListener.getScenario().getNetwork().getNodes().size());

		 delete3.undoCommand();
		 Assert.assertEquals(11,intersectionsListener.getScenario().getNetwork().getLinks().size());
		 Assert.assertEquals(12,intersectionsListener.getScenario().getNetwork().getNodes().size());
	}

    private long countRoutes(TransitSchedule transitSchedule) {
        int result = 0;
        for (TransitLine transitLine : transitSchedule.getTransitLines().values()) {
            result += transitLine.getRoutes().size();
        }
        return result;
    }

}
