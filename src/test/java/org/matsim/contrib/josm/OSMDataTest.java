package org.matsim.contrib.josm;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.openstreetmap.josm.data.osm.DataSet;
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
	    private OsmDataLayer busRouteLayer;
	    private NetworkListener busRouteListener;

	    @Before
	    public void init() {
	        new JOSMFixture(folder.getRoot().getPath()).init(false);
	        URL urlIncompleteWay = getClass().getResource("/test-input/OSMData/incompleteWay.osm");
	        URL urlRoute = getClass().getResource("/test-input/OSMData/busRoute.osm");
		       
	        InputStream incompleteWayInput = null;
	        InputStream busRouteInput = null; 
			try {
				incompleteWayInput = Compression.getUncompressedFileInputStream(new File(urlIncompleteWay.getFile()));
				busRouteInput = Compression.getUncompressedFileInputStream(new File(urlRoute.getFile()));
			} catch (IOException e) {
				e.printStackTrace();
			}
	    	DataSet incompleteWayData = null;
	    	DataSet busRouteData = null;
			try {
				incompleteWayData = OsmReader.parseDataSet(incompleteWayInput, null);
				busRouteData = OsmReader.parseDataSet(busRouteInput, null);
			} catch (IllegalDataException e) {
				e.printStackTrace();
			}
	    	incompleteWayLayer = new OsmDataLayer(incompleteWayData, "test", null);
	    	busRouteLayer = new OsmDataLayer(busRouteData, "test", null);
	    	busRouteListener = new NetworkListener(busRouteData, ScenarioUtils.createScenario(ConfigUtils.createConfig()), new HashMap<Way, List<Link>>(), new HashMap<Link, List<WaySegment>>(), new HashMap<Relation, TransitRoute>());
            busRouteListener.visitAll();
	    
	    }

	    @Test
	    public void testIncompleteWay() throws InterruptedException, ExecutionException, IOException, IllegalDataException {
	    	  LayerConverter converter =  new LayerConverter(incompleteWayLayer);
		        converter.run();
	    	Assert.assertEquals(0,converter.getMatsimLayer().data.getWays().size());
	        Assert.assertEquals(0,converter.getMatsimLayer().getScenario().getNetwork().getLinks().size());
	        
	    }
	    
	    @Test
	    public void testBusRoute() throws InterruptedException, ExecutionException, IOException, IllegalDataException {
		        Assert.assertEquals(18,busRouteListener.getScenario().getNetwork().getLinks().size());
	            Assert.assertEquals(10,busRouteListener.getScenario().getNetwork().getNodes().size());
	            Assert.assertEquals(4,busRouteListener.getScenario().getTransitSchedule().getFacilities().size());
	            Assert.assertEquals(1,busRouteListener.getScenario().getTransitSchedule().getTransitLines().size());
	            Assert.assertEquals(2,busRouteListener.getScenario().getTransitSchedule().getTransitLines().get(Id.create("-116", TransitRoute.class)).getRoutes().size());
		    	for (TransitRoute route: busRouteListener.getScenario().getTransitSchedule().getTransitLines().get(Id.create("-116", TransitRoute.class)).getRoutes().values()) {
		    		Assert.assertEquals(4, route.getStops().size());
		    		Assert.assertEquals(9, route.getRoute().getLinkIds().size());
		    	}
		    	LayerConverter converter =  new LayerConverter(busRouteLayer);
		    	converter.run();
	        
	    }

	    
	    
	 

	   

	
}
