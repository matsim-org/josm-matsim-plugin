package org.matsim.contrib.josm;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.projection.Projection;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;

/**
 * The task which is executed after confirming the ImportDialog. Creates a new
 * layer showing the network data.
 * 
 * @author Nico
 */
class ImportTask extends PleaseWaitRunnable {

	/**
	 * The String representing the id tagging-key for nodes.
	 */
	public static final String NODE_TAG_ID = "id";
	/**
	 * The String representing the id tagging-key for ways.
	 */
	public static final String WAY_TAG_ID = "id";

    private final Importer importer;

    /**
	 * Creates a new Import task with the given <code>path</code>.
	 */
	public ImportTask(String networkPath, String schedulePath, Projection projection) {
		super("MATSim Import");
		this.importer = new Importer(networkPath, schedulePath, projection);
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
