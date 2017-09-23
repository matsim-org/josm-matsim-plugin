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
		if (!dialog.isCanceled()) {
			dialog.rememberSettings();
			Bounds area = dialog.getSelectedDownloadArea().get();
			DownloadOsmTask task = new DownloadOsmTask();
			MainApplication.worker.submit(new PostDownloadHandler(task, task.download(new FilteredDownloader(area), dialog.isNewLayerRequired(), area, null)));
		}
	}

	private static class FilteredDownloader extends OsmServerReader {

		private static final int TIMEOUT_S = 600;
		/**
		 * The boundings of the desired map data.
		 */
		protected final double lat1;
		protected final double lon1;
		protected final double lat2;
		protected final double lon2;
		protected final boolean crosses180th;
		private static final String API = "http://overpass-api.de/api/interpreter?data=";

		public FilteredDownloader(Bounds downloadArea) {
			CheckParameterUtil.ensureParameterNotNull(downloadArea, "downloadArea");
			this.lat1 = downloadArea.getMinLat();
			this.lon1 = downloadArea.getMinLon();
			this.lat2 = downloadArea.getMaxLat();
			this.lon2 = downloadArea.getMaxLon();
			this.crosses180th = downloadArea.crosses180thMeridian();
		}

		@Override
		public DataSet parseOsm(ProgressMonitor progressMonitor) throws OsmTransferException {
			progressMonitor.beginTask(tr("Contacting OSM Server..."), 10);
			DataSet ds = null;
			String highwayPredicates = getHighwayPredicates();
			String routePredicates = getRoutePredicates();

			if (highwayPredicates == null && routePredicates == null) {
				return null;
			}

			try {
				DataSet dsTemp;
				if (crosses180th) {
					// API 0.6 does not support requests crossing the 180th
					// meridian, so make two requests
					DataSet ds2;

					try (InputStream in = getInputStream(getUrl(highwayPredicates, routePredicates, lon1, lat1, 180.0, lat2),
							progressMonitor.createSubTaskMonitor(9, false))) {
						if (in == null)
							return null;
						dsTemp = OsmReader.parseDataSet(in, progressMonitor.createSubTaskMonitor(1, false));
					}

					try (InputStream in = getInputStream(getUrl(highwayPredicates, routePredicates, -180.0, lat1, lon2, lat2),
							progressMonitor.createSubTaskMonitor(9, false))) {
						if (in == null)
							return null;
						ds2 = OsmReader.parseDataSet(in, progressMonitor.createSubTaskMonitor(1, false));
					}
					if (ds2 == null)
						return null;
					dsTemp.mergeFrom(ds2);

				} else {
					// Simple request
					try (InputStream in = getInputStream(getUrl(highwayPredicates, routePredicates, lon1, lat1, lon2, lat2),
							progressMonitor.createSubTaskMonitor(9, false))) {
						if (in == null)
							return null;
						dsTemp = OsmReader.parseDataSet(in, progressMonitor.createSubTaskMonitor(1, false));
					}
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

		private String getRoutePredicates() {
			int counter = 0;
			StringBuilder routes = new StringBuilder("[\"route\"~\"");
			for (String route : OsmConvertDefaults.routeTypes) {
				if (new BooleanProperty("matsim_download_" + route, true).get()) {
					routes.append(route);
					routes.append("|");
					counter++;
				}
			}

			if (routes.lastIndexOf("|") != -1) {
				routes.replace(routes.lastIndexOf("|"), routes.lastIndexOf("|") + 1, "");
			}
			routes.append("\"]");
			if (counter == 0) {
				return null;
			}
			return routes.toString();
		}

		private String getHighwayPredicates() {
			int counter = 0;
			StringBuilder highways = new StringBuilder("[\"highway\"~\"");
			for (String highway : OsmConvertDefaults.highwayTypes) {
				if (new BooleanProperty("matsim_download_" + highway, true).get()) {
					highways.append(highway);
					highways.append("|");
					counter++;
				}
			}
			if (highways.lastIndexOf("|") != -1) {
				highways.replace(highways.lastIndexOf("|"), highways.lastIndexOf("|") + 1, "");
			}
			highways.append("\"]");
			if (counter == 0) {
				return null;
			}
			return highways.toString();
		}

		private String getUrl(String highwayPredicates, String routePredicates, double lon1, double lat1, double lon2, double lat2) {
			return API+ Utils.encodeUrl(getQuery(highwayPredicates, routePredicates, lon1, lat1, lon2, lat2));
		}

		private String getQuery(String highwayPredicates, String routePredicates, double lon1, double lat1, double lon2, double lat2) {
			StringBuilder sb = new StringBuilder();
			sb.append(String.format("[timeout:%d];", TIMEOUT_S));
			String bbox = "(" + lat1 + "," + lon1 + "," + lat2 + "," + lon2 + ")";
			if (highwayPredicates != null) {
				sb.append(String.format("way %s %s -> .highways;", bbox, highwayPredicates));
			}
			if (routePredicates != null) {
				sb.append(String.format("node %s; rel(bn) %s -> .routes;", bbox, routePredicates));
				sb.append("rel (br.routes) [type=route_master] -> .route_masters;");
				sb.append("(.routes; rel (r.route_masters);) -> .routes;");
				sb.append("(node(r.routes:stop)->.x; node(r.routes:platform)->.x;) -> .stops_and_platforms;");
				sb.append("rel(bn.stops_and_platforms)[public_transport=stop_area] -> .stop_areas;");
				sb.append("(.route_masters>>; .routes>>; .stop_areas>>;) -> .all_transit;");
			}
			sb.append("(");
			if (highwayPredicates != null) {
				sb.append(".highways;");
				sb.append(".highways>;");
			}
			if (routePredicates != null) {
				sb.append(".all_transit;");
			}
			sb.append(");");
			sb.append("out meta;");
			return sb.toString();
		}

	}
}
