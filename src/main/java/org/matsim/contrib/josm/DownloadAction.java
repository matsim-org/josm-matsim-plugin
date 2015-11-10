package org.matsim.contrib.josm;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.util.concurrent.Future;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.actions.downloadtasks.PostDownloadHandler;
import org.openstreetmap.josm.data.Bounds;

/**
 * Action that opens a connection to the osm server and downloads MATSim-related
 * map data.
 *
 * An dialog is displayed asking the user to specify a rectangle to grab. The
 * url and account settings from the preferences are used.
 */
public class DownloadAction extends JosmAction {

	/**
	 * Constructs a new {@code DownloadAction}.
	 */
	public DownloadAction() {
		super(tr("Download from Overpass API ..."), null, tr("Download data from Overpass API, filtered by relevance for MATSim."), null, true);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		DownloadDialog dialog = DownloadDialog.getInstance();
		dialog.restoreSettings();
		dialog.setVisible(true);
		if (!dialog.isCanceled()) {
			dialog.rememberSettings();
			Bounds area = dialog.getSelectedDownloadArea();
			DownloadMATSimOsmTask task = new DownloadMATSimOsmTask();
			Future<?> future = task.download(dialog.isNewLayerRequired(), area, null);
			Main.worker.submit(new PostDownloadHandler(task, future));
		}
	}
}
