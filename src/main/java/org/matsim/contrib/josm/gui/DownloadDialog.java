package org.matsim.contrib.josm.gui;

import org.matsim.contrib.josm.model.OsmConvertDefaults;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.downloadtasks.DownloadOsmTask;
import org.openstreetmap.josm.actions.downloadtasks.PostDownloadHandler;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.io.OnlineResource;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.io.OsmServerReader;
import org.openstreetmap.josm.io.OsmTransferException;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.InputStream;

import static org.openstreetmap.josm.tools.I18n.tr;

/**
 * Dialog displayed to download OSM data from OSM server.
 */
public class DownloadDialog extends org.openstreetmap.josm.gui.download.DownloadDialog {

	private static DownloadDialog instance;

	public DownloadDialog(Component comp) {
		super(comp);
		buildMainPanelAboveDownloadSelections(this.mainPanel);
	}

	protected void buildMainPanelAboveDownloadSelections(JPanel pnl) {

		JPanel contentPnl = new JPanel(new GridLayout(2, 1));

		ActionListener cbListener = e -> {
			JCheckBox cb = (JCheckBox) e.getSource();
			Config.getPref().putBoolean("matsim_download_" + cb.getText(), cb.isSelected());
		};

		JPanel highwaysPnl = new JPanel(new FlowLayout());
		highwaysPnl.add(new JLabel("Highways:"));
		for (String highwayType : OsmConvertDefaults.highwayTypes) {
			JCheckBox cb = new JCheckBox(highwayType);
			cb.setToolTipText(tr("Select to download " + cb.getText() + " highways in the selected download area."));
			cb.setSelected(Config.getPref().getBoolean("matsim_download_" + cb.getText(), true));
			cb.addActionListener(cbListener);
			highwaysPnl.add(cb, GBC.std());
		}
		contentPnl.add(highwaysPnl);

		JPanel routesPnl = new JPanel(new FlowLayout());
		routesPnl.add(new JLabel("Routes:"));
		for (String routeType : OsmConvertDefaults.routeTypes) {
			JCheckBox cb = new JCheckBox(routeType);
			cb.setToolTipText(tr("Select to download " + cb.getText() + " routes in the selected download area."));
			cb.setSelected(Config.getPref().getBoolean("matsim_download_" + cb.getText(), true));
			cb.addActionListener(cbListener);
			routesPnl.add(cb, GBC.std());
		}
		contentPnl.add(routesPnl);
		this.btnDownload.setAction(new DownloadAction());
		pnl.add(contentPnl, GBC.eol());
	}

	/**
	 * Replies true if the user selected to download OSM data
	 *
	 * @return true if the user selected to download OSM data
	 */
	public boolean isDownloadOsmData() {
		return true;
	}

	/**
	 * Replies true if the user selected to download GPX data
	 *
	 * @return true if the user selected to download GPX data
	 */
	public boolean isDownloadGpxData() {
		return false;
	}

	/**
	 * Replies the unique instance of the download dialog
	 *
	 * @return the unique instance of the download dialog
	 */
	public static synchronized DownloadDialog getInstance() {
		if (instance == null) {
			instance = new DownloadDialog(Main.parent);
		}
		return instance;
	}

	/**
	 * Remembers the current settings in the download dialog.
	 */
	@Override
	public void rememberSettings() {
		if (currentBounds != null) {
			Config.getPref().put("osm-download.bounds", currentBounds.encodeAsString(";"));
		}
	}

	class DownloadAction extends AbstractAction {
		DownloadAction() {
			this.putValue("Name", I18n.tr("Download", new Object[0]));
			(new ImageProvider("download")).getResource().attachImageIcon(this);
			this.putValue("ShortDescription", I18n.tr("Click to download the currently selected area", new Object[0]));
			this.setEnabled(!Main.isOffline(OnlineResource.OSM_API));
		}

		public void run() {
			rememberSettings();
			Bounds area = getSelectedDownloadArea().get();
			DownloadOsmTask task = new DownloadOsmTask();
			MainApplication.worker.submit(new PostDownloadHandler(task, task.download(new FilteredDownloader(area), isNewLayerRequired(), area, null)));
			dispose();
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			this.run();
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
						if (in == null) {
							return null;
						}
						dsTemp = OsmReader.parseDataSet(in, progressMonitor.createSubTaskMonitor(1, false));
					}

					try (InputStream in = getInputStream(getUrl(highwayPredicates, routePredicates, -180.0, lat1, lon2, lat2),
							progressMonitor.createSubTaskMonitor(9, false))) {
						if (in == null) {
							return null;
						}
						ds2 = OsmReader.parseDataSet(in, progressMonitor.createSubTaskMonitor(1, false));
					}
					if (ds2 == null) {
						return null;
					}
					dsTemp.mergeFrom(ds2);

				} else {
					// Simple request
					try (InputStream in = getInputStream(getUrl(highwayPredicates, routePredicates, lon1, lat1, lon2, lat2),
							progressMonitor.createSubTaskMonitor(9, false))) {
						if (in == null) {
							return null;
						}
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
