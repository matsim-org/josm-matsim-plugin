package org.matsim.contrib.josm.actions;

import org.matsim.contrib.josm.model.OsmConvertDefaults;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.actions.downloadtasks.DownloadOsmTask;
import org.openstreetmap.josm.actions.downloadtasks.PostDownloadHandler;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.io.OsmServerReader;
import org.openstreetmap.josm.io.OsmTransferException;
import org.openstreetmap.josm.tools.CheckParameterUtil;
import org.openstreetmap.josm.tools.HttpClient;

import java.awt.event.ActionEvent;
import java.io.InputStream;
import java.util.concurrent.Future;

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
		DownloadOsmTask task = new DownloadOsmTask() {
			@Override
			public Future<?> download(boolean newLayer, Bounds downloadArea, ProgressMonitor progressMonitor) {
				return download(new VBBDownloader(), newLayer, downloadArea, progressMonitor);
			}
		};
		Future<?> future = task.download(true, new Bounds(0,0,true), null);
		Main.worker.submit(new PostDownloadHandler(task, future));
	}

	private static class VBBDownloader extends OsmServerReader {

		private static final int TIMEOUT_S = 600;
		private static final String API = "http://overpass-api.de/api/interpreter?data=";

		@Override
		public DataSet parseOsm(ProgressMonitor progressMonitor) throws OsmTransferException {
			progressMonitor.beginTask(tr("Contacting OSM Server..."), 10);
			DataSet ds = null;
			try {
				DataSet dsTemp;
				try (InputStream in = getInputStream(getUrl(),
						progressMonitor.createSubTaskMonitor(9, false))) {
					if (in == null)
						return null;
					dsTemp = OsmReader.parseDataSet(in, progressMonitor.createSubTaskMonitor(1, false));
				}
				dsTemp.mergeFrom(ds);
				ds = dsTemp;
			} catch (OsmTransferException e) {
				throw e;
			} catch (Exception e) {
				throw new OsmTransferException(e);
			} finally {
				activeConnection = null;

			}
			progressMonitor.finishTask();
			return ds;
		}

		@Override
		protected void adaptRequest(HttpClient httpClient) {
			httpClient.setConnectTimeout(TIMEOUT_S * 1000);
			httpClient.setReadTimeout(TIMEOUT_S * 1000);
		}

		private String getUrl() {
			return API+getQuery();
		}

		private String getQuery() {
			return String.format("[timeout:%d];", TIMEOUT_S) +
					"rel[\"type\"=\"route_master\"][\"network\"=\"Verkehrsverbund Berlin-Brandenburg\"];" +
					"out meta;";
		}

	}
}
