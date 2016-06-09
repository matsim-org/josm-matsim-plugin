package org.matsim.contrib.josm;

import java.io.File;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;

/**
 * The task which is executed after confirming the ImportDialog. Creates a new
 * layer showing the network data.
 *
 * @author Nico
 */
class ImportTask extends PleaseWaitRunnable {

    private final Importer importer;

    /**
     * Creates a new Import task with the given <code>path</code>.
     */
    public ImportTask(File network, File schedule) {
        super("MATSim Import");
        this.importer = new Importer(network, schedule);
    }

    /**
     * @see org.openstreetmap.josm.gui.PleaseWaitRunnable#cancel()
     */
    @Override
    protected void cancel() {
        // TODO Auto-generated method stub
    }

    /**
     * @see org.openstreetmap.josm.gui.PleaseWaitRunnable#finish()
     */
    @Override
    protected void finish() {
        MATSimLayer layer = importer.getLayer();
        // layer = null happens if Exception happens during import,
        // as Exceptions are handled only after this method is called.
        if (layer != null) {
            Main.main.addLayer(layer);
            Main.map.mapView.setActiveLayer(layer);
        }
    }

    /**
     * @see org.openstreetmap.josm.gui.PleaseWaitRunnable#realRun()
     */
    @Override
    protected void realRun() {
        importer.run();
    }

}
