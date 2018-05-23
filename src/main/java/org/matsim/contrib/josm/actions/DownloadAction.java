package org.matsim.contrib.josm.actions;

import org.matsim.contrib.josm.gui.DownloadDialog;
import org.matsim.contrib.josm.model.OsmConvertDefaults;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.actions.downloadtasks.DownloadOsmTask;
import org.openstreetmap.josm.actions.downloadtasks.PostDownloadHandler;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.io.OsmServerReader;
import org.openstreetmap.josm.io.OsmTransferException;
import org.openstreetmap.josm.tools.CheckParameterUtil;
import org.openstreetmap.josm.tools.HttpClient;
import org.openstreetmap.josm.tools.Utils;

import java.awt.event.ActionEvent;
import java.io.InputStream;

import static org.openstreetmap.josm.tools.I18n.tr;

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
		super(tr("Download from Overpass API..."), null, tr("Download data from Overpass API, filtered by relevance for MATSim."), null, true);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		DownloadDialog dialog = DownloadDialog.getInstance();
		dialog.restoreSettings();
		dialog.setVisible(true);
	}

}
