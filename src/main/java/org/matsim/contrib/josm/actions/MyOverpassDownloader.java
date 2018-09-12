package org.matsim.contrib.josm.actions;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.InputStream;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.io.OsmServerReader;
import org.openstreetmap.josm.io.OsmTransferException;
import org.openstreetmap.josm.tools.HttpClient;
import org.openstreetmap.josm.tools.Utils;

public final class MyOverpassDownloader extends OsmServerReader {
	public static final int TIMEOUT_S = 600;
	private static final String API = "http://overpass-api.de/api/interpreter?data=";

	private final String query;

	public MyOverpassDownloader(String query) {
		this.query = query;
	}

	@Override
	public DataSet parseOsm(ProgressMonitor progressMonitor) throws OsmTransferException {
		progressMonitor.beginTask(tr("Contacting OSM Server..."), 10);
		DataSet ds = null;
		try {
			DataSet dsTemp;
			try (InputStream in = getInputStream(getUrl(), progressMonitor.createSubTaskMonitor(9, false))) {
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
		return API+ Utils.encodeUrl(query);
	}

}
