package org.matsim.contrib.josm;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.josm.scenario.EditableScenarioUtils;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.ChangeNodesCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.io.Compression;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.io.OsmReader;


public class OSMDataTest {

	
	    @Rule
	    public TemporaryFolder folder = new TemporaryFolder();
	    
	    private OsmDataLayer incompleteWayLayer;
	    private NetworkListener incompleteWayListener;
	    private OsmDataLayer busRouteLayer;
	    private NetworkListener busRouteListener;
	    private OsmDataLayer intersectionsLayer;
	    private NetworkListener intersectionsListener;

	    @Before
	    public void init() {
	        new JOSMFixture(folder.getRoot().getPath()).init(false);
            OsmConvertDefaults.load();
            Main.pref.put("matsim_supportTransit", true);
	        URL urlIncompleteWay = getClass().getResource("/test-input/OSMData/incompleteWay.osm.xml");
	        URL urlRoute = getClass().getResource("/test-input/OSMData/busRoute.osm.xml");
	        URL urlIntersections = getClass().getResource("/test-input/OSMData/loops_intersecting_ways.osm");
		       
	        InputStream incompleteWayInput = null;
	        InputStream busRouteInput = null; 
	        InputStream intersectionsInput = null; 
			try {
				incompleteWayInput = Compression.getUncompressedFileInputStream(new File(urlIncompleteWay.getFile()));
				busRouteInput = Compression.getUncompressedFileInputStream(new File(urlRoute.getFile()));
				intersectionsInput = Compression.getUncompressedFileInputStream(new File(urlIntersections.getFile()));
			} catch (IOException e) {
				e.printStackTrace();
			}
	    	DataSet incompleteWayData = null;
	    	DataSet busRouteData = null;
	    	DataSet intersectionsData = null;
			try {
				incompleteWayData = OsmReader.parseDataSet(incompleteWayInput, null);
				busRouteData = OsmReader.parseDataSet(busRouteInput, null);
				intersectionsData = OsmReader.parseDataSet(intersectionsInput, null);
			} catch (IllegalDataException e) {
				e.printStackTrace();
			}
	    	incompleteWayLayer = new OsmDataLayer(incompleteWayData, "test", null);
	    	busRouteLayer = new OsmDataLayer(busRouteData, "test", null);
	    	intersectionsLayer = new OsmDataLayer(intersectionsData, "test", null);
            Config config = ConfigUtils.createConfig();
            config.scenario().setUseTransit(true);
            config.scenario().setUseVehicles(true);
            busRouteListener = new NetworkListener(busRouteData, EditableScenarioUtils.createScenario(config), new HashMap<Way, List<Link>>(), new HashMap<Link, List<WaySegment>>(), new HashMap<Relation, TransitRoute>());
            Main.pref.addPreferenceChangeListener(busRouteListener);
            busRouteListener.visitAll();
            incompleteWayListener = new NetworkListener(incompleteWayData, EditableScenarioUtils.createScenario(config), new HashMap<Way, List<Link>>(), new HashMap<Link, List<WaySegment>>(), new HashMap<Relation, TransitRoute>());
            incompleteWayListener.visitAll();
            intersectionsListener = new NetworkListener(busRouteData, EditableScenarioUtils.createScenario(config), new HashMap<Way, List<Link>>(), new HashMap<Link, List<WaySegment>>(), new HashMap<Relation, TransitRoute>());
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
	    	List<Node> nodes = new ArrayList<Node>();
	    	for (Node node: incompleteWayLayer.data.getNodes()) {
	    		if(!node.isIncomplete()) {
	    			nodes.add(node);
	    		}
	    	}
	    	
	    	List<Command> commands = new ArrayList<>();
	    	Assert.assertEquals(0,incompleteWayListener.getScenario().getNetwork().getNodes().size());
	    	Command changeNodes = new ChangeNodesCommand(way, nodes);
	    	commands.add(changeNodes);
	    	changeNodes.executeCommand();
	    	Assert.assertEquals(1,incompleteWayLayer.data.getWays().size());
	    	Assert.assertEquals(4,incompleteWayListener.getScenario().getNetwork().getNodes().size());
	    	Assert.assertEquals(6,incompleteWayListener.getScenario().getNetwork().getLinks().size());
	    	
	    	nodes.remove(way.lastNode());
	    	Command changeNodes2 = new ChangeNodesCommand(way, nodes);
	    	commands.add(changeNodes2);
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
            
            Scenario simulatedExportScenario = TransitScheduleExporter.convertIdsAndFilterDeleted(converter.getMatsimLayer().getScenario());
            Assert.assertEquals("busRoute", simulatedExportScenario.getTransitSchedule().getTransitLines().values().iterator().next().getId().toString());
           
        }
	    
//	    @Test
//	    public void testIntersections() {
//	    	 Assert.assertEquals(11,busRouteListener.getScenario().getNetwork().getLinks().size());
//	         Assert.assertEquals(12,busRouteListener.getScenario().getNetwork().getNodes().size());
//	    }

    private long countRoutes(TransitSchedule transitSchedule) {
        int result = 0;
        for (TransitLine transitLine : transitSchedule.getTransitLines().values()) {
            result += transitLine.getRoutes().size();
        }
        return result;
    }
	    
	 

	   

	
}
