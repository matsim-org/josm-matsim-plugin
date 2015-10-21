package org.matsim.contrib.josm;

import java.io.IOException;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.io.OsmTransferException;
import org.xml.sax.SAXException;

/**
 * The Task that handles the convert action. Creates new OSM primitives with
 * MATSim Tag scheme
 * 
 * @author Nico
 * 
 */

class ConvertTask extends PleaseWaitRunnable {

    private final LayerConverter converter;

    /**
     * Creates a new Convert task
     * 
     * @see PleaseWaitRunnable
     */
    public ConvertTask() {
	super("Converting to MATSim Network");
	this.converter = new LayerConverter((OsmDataLayer) Main.main.getActiveLayer());
    }

    /**
     * @see org.openstreetmap.josm.gui.PleaseWaitRunnable#cancel()
     */
    @Override
    protected void cancel() {
	// TODO Auto-generated method stub
    }

    /**
     * @see org.openstreetmap.josm.gui.PleaseWaitRunnable#realRun()
     */
    @Override
    protected void realRun() throws SAXException, IOException, OsmTransferException {
	this.converter.run();
    }

    /**
     * @see org.openstreetmap.josm.gui.PleaseWaitRunnable#finish()
     */
    @Override
    protected void finish() {
	if (converter.getMatsimLayer() != null) {
	    // Do not zoom to full layer extent, but leave the view port where
	    // it is.
	    // (Perhaps I want to look at the particular are I am viewing right
	    // now.)
	    ProjectionBounds projectionBounds = null;
	    Main.main.addLayer(converter.getMatsimLayer(), projectionBounds);
	}
    }
}
