package org.matsim.contrib.josm;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.InputStream;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.io.OsmServerReader;
import org.openstreetmap.josm.io.OsmTransferException;
import org.openstreetmap.josm.tools.CheckParameterUtil;

public class FilteredDownloader extends OsmServerReader {

    /**
     * The boundings of the desired map data.
     */
    protected final double lat1;
    protected final double lon1;
    protected final double lat2;
    protected final double lon2;
    protected final boolean crosses180th;
    private static final String API = "http://www.overpass-api.de/api/xapi_meta?";

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
	
	for (int i = 0; i < OsmConvertDefaults.wayTypes.length; i++) {
	    String highway = OsmConvertDefaults.wayTypes[i];
	    if(!Main.pref.getBoolean("matsim_download_"+highway, true)) {
		continue;
	    }
	    String object = "way[highway="+highway+"]";
		progressMonitor.indeterminateSubTask(object);
	    try {
		DataSet dsTemp = null;
		if (crosses180th) {
		    // API 0.6 does not support requests crossing the 180th
		    // meridian, so make two requests
		    DataSet ds2 = null;

		    try (InputStream in = getInputStream(getRequest(object, lon1, lat1, 180.0, lat2), progressMonitor.createSubTaskMonitor(9, false))) {
			if (in == null)
			    return null;
			dsTemp = OsmReader.parseDataSet(in, progressMonitor.createSubTaskMonitor(1, false));
		    }

		    try (InputStream in = getInputStream(getRequest(object, -180.0, lat1, lon2, lat2), progressMonitor.createSubTaskMonitor(9, false))) {
			if (in == null)
			    return null;
			ds2 = OsmReader.parseDataSet(in, progressMonitor.createSubTaskMonitor(1, false));
		    }
		    if (ds2 == null)
			return null;
		    dsTemp.mergeFrom(ds2);

		} else {
		    // Simple request
		    try (InputStream in = getInputStream(getRequest(object, lon1, lat1, lon2, lat2), progressMonitor.createSubTaskMonitor(9, false))) {
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
	}
	progressMonitor.finishTask();
	return ds;
    }

    private String getRequest(String object, double lon1, double lat1, double lon2, double lat2) {
	StringBuilder sb = new StringBuilder(API);
	sb.append(object);
	sb.append("[bbox=" + lon1 + "," + lat1 + "," + lon2 + "," + lat2 + "]");
	return sb.toString();

    }

}
