package org.matsim.contrib.josm.actions;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.actions.downloadtasks.DownloadOsmTask;
import org.openstreetmap.josm.actions.downloadtasks.PostDownloadHandler;
import org.openstreetmap.josm.data.Bounds;

import java.awt.event.ActionEvent;

import static org.openstreetmap.josm.tools.I18n.tr;

/**
 * Action that opens a connection to the osm server and downloads MATSim-related
 * map data.
 *
 * An dialog is displayed asking the user to specify a rectangle to grab. The
 * url and account settings from the preferences are used.
 */
public class DownloadVBBAction extends JosmAction {

	/**
	 * Constructs a new {@code DownloadAction}.
	 */
	public DownloadVBBAction() {
		super(tr("Download VBB..."), null, tr("Download VBB."), null, true);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		DownloadOsmTask task = new DownloadOsmTask();
		String query =
				String.format("[timeout:%d];", MyOverpassDownloader.TIMEOUT_S) +
				"rel[\"type\"=\"route_master\"][\"network\"=\"Verkehrsverbund Berlin-Brandenburg\"];" +
				"out meta;";
		Main.worker.submit(new PostDownloadHandler(task, task.download(new MyOverpassDownloader(query) ,true, new Bounds(0,0,true), null)));
	}

}
