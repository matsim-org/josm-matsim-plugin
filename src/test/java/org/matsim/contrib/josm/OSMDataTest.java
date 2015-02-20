package org.matsim.contrib.josm;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.ExecutionException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.io.Compression;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.io.OsmReader;


public class OSMDataTest {

	
	    @Rule
	    public TemporaryFolder folder = new TemporaryFolder();

	    @Before
	    public void init() {
	        new JOSMFixture(folder.getRoot().getPath()).init(false);
	    }

	    @Test
	    public void readOsmFile() throws InterruptedException, ExecutionException, IOException, IllegalDataException {
	        URL url = getClass().getResource("/test-input/OSMData/incompleteWay.osm");
	        loadIncompleteWay(new File(url.getFile()));
	    }

	    private void loadIncompleteWay(File file) throws InterruptedException, ExecutionException, IOException, IllegalDataException {
	    	InputStream in = Compression.getUncompressedFileInputStream(file);
	    	DataSet data = OsmReader.parseDataSet(in, null);
	    	OsmDataLayer layer = new OsmDataLayer(data, "test", file);
	        LayerConverter converter =  new LayerConverter(layer);
	        converter.run();
	        Assert.assertEquals(0,converter.getMatsimLayer().data.getWays().size());
	        Assert.assertEquals(0,converter.getMatsimLayer().getScenario().getNetwork().getLinks().size());
	    }

	   

	
}
